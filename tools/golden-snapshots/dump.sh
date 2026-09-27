#!/usr/bin/env bash
# Съёмка эталонов выгрузки с платформы 1С через ibcmd: без конфигуратора и лицензии.
#
# Раскладывает результат как fixtures/samples-1c-platform/snapshots/<формат>/:
#   cf-empty-infobase/   выгрузка конфигурации пустой ИБ
#   cf-bare-objects/     голый объект каждого вида (семя seed/, зависимости seed-deps/ срезаются)
#   cf-object-nodes/     объекты-владельцы с узлом каждого вида: реквизит, табличная часть, команда...
#   cfe-empty/           пустое расширение
#   external-files/      внешние отчёт и обработка: empty/ и empty-full-objects/
# Формат определяется по атрибуту version выгрузки. Сводка «что снято каким путём» пишется
# в $OUT/summary-<версия платформы>.txt.
#
# Использование:
#   dump.sh                  снять эталоны
#   dump.sh build-external   только собрать .epf/.erf из EXT_FULL_SRC в EXT_BIN (нужна платформа 8.3.27+)
#   dump.sh nodes            только переснять cf-object-nodes (остальное в $OUT/<формат> не трогается)
#
# Переменные окружения:
#   BIN           каталог с ibcmd (по умолчанию ищется в /opt/1cv8 и /opt/1C)
#   OUT           корень результата (по умолчанию tools/golden-snapshots/out)
#   WORK          рабочий каталог (по умолчанию временный; на Windows путь должен быть коротким)
#   PYTHON        интерпретатор Python 3 (по умолчанию python3, python или py)
#   EXT_FULL_SRC  исходник empty-full-objects в формате 2.20
#                 (по умолчанию fixtures/samples-1c-platform/snapshots/2.20/external-files/empty-full-objects)
#   EXT_BIN       каталог с готовыми .epf/.erf для empty-full-objects (собраны платформой 8.3.27+)
#   BUILDER_BIN   каталог ibcmd 8.3.27+, которым собрать .epf/.erf, если своя платформа старше
#   VERIFY_ROUTES 1 (по умолчанию): если в ibcmd есть import files --no-check, снять голые объекты
#                 и узлы и этим маршрутом и сверить с маршрутом «импорт + срезка» побайтно
#   KEEP_WORK     не удалять рабочий каталог (журналы ibcmd в $WORK/logs)
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
OUT="${OUT:-$HERE/out}"
EXT_FULL_SRC="${EXT_FULL_SRC:-$REPO/fixtures/samples-1c-platform/snapshots/2.20/external-files/empty-full-objects}"
VERIFY_ROUTES="${VERIFY_ROUTES:-1}"
EXT_NAME="ПустоеРасширение"
MODE="${1:-dump}"

die() {
	echo "ERROR: $*" >&2
	exit 1
}

# На Windows утилиты платформы и Python понимают только пути Windows.
topath() {
	if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}

find_ibcmd() {
	local dir=$1
	if [ -n "$dir" ]; then
		ls "$dir"/ibcmd "$dir"/ibcmd.exe 2>/dev/null | head -1
	else
		find /opt/1cv8 /opt/1C -name 'ibcmd' -type f 2>/dev/null | sort -V | tail -1
	fi
}

IBCMD=$(find_ibcmd "${BIN:-}")
[ -n "$IBCMD" ] || die "ibcmd не найден, задайте BIN"

TIMEOUT=()
command -v timeout >/dev/null 2>&1 && TIMEOUT=(timeout 1800)

# На Windows python3 может оказаться заглушкой магазина приложений: берём первый, что отвечает.
PY="${PYTHON:-}"
if [ -z "$PY" ]; then
	for CAND in python3 python py; do
		if [ "$("${TIMEOUT[@]}" "$CAND" -c "print('ok')" 2>/dev/null)" = "ok" ]; then PY="$CAND"; break; fi
	done
fi
[ -n "$PY" ] || die "не найден Python 3, задайте PYTHON"
snap() { "$PY" "$(topath "$HERE/snapshot.py")" "$@"; }

# Язык процесса влияет на имена по умолчанию (роль расширения, вариант языка): эталоны русские.
if locale -a 2>/dev/null | grep -qi '^ru_RU\.utf-\?8$'; then
	export LANG=ru_RU.UTF-8 LC_ALL=ru_RU.UTF-8
fi

if [ -z "${WORK:-}" ]; then
	WORK=$(mktemp -d)
	[ -n "${KEEP_WORK:-}" ] || trap 'rm -rf "$WORK"' EXIT
fi
mkdir -p "$WORK/logs" "$WORK/ib"
LOGS="$WORK/logs"

PLATFORM=$("$IBCMD" --version 2>/dev/null | tr -d '\r' | head -1)
[ -n "$PLATFORM" ] || die "ibcmd не отвечает: $IBCMD"
HELP=$({ "$IBCMD" help config; "$IBCMD" help infobase; } 2>&1)
has_help() { grep -q -- "$1" <<<"$HELP"; }

# Запуск ibcmd с журналом: run <журнал> <аргументы...>
run() {
	local log="$LOGS/$1"
	shift
	echo "\$ $*" >"$log"
	"${TIMEOUT[@]}" "$@" >>"$log" 2>&1
	local rc=$?
	echo "[rc=$rc]" >>"$log"
	return $rc
}

show_log() {
	echo "--- журнал $1:"
	grep -v '^\$ ' "$LOGS/$1" | tail -"${2:-30}"
}

# --data есть у ibcmd 8.3.20+, у 8.3.19 его нет: пробуем с ним, затем без.
DATA_MODE=""
ib_create() {
	local name=$1 dir="$WORK/ib/$1"
	rm -rf "$dir"
	mkdir -p "$dir/db" "$dir/data"
	if [ "$DATA_MODE" != "no" ] && run "create-$name.log" "$IBCMD" infobase create \
		--db-path="$(topath "$dir/db")" --data="$(topath "$dir/data")" --locale=ru; then
		DATA_MODE=yes
		return 0
	fi
	[ "$DATA_MODE" = "yes" ] && return 1
	run "create-$name.log" "$IBCMD" infobase create --db-path="$(topath "$dir/db")" --locale=ru || return 1
	DATA_MODE=no
}

# Команда над ИБ: ibc <журнал> <ИБ> <слова команды...> -- <аргументы...>
ibc() {
	local log=$1 name=$2 dir="$WORK/ib/$2"
	shift 2
	local words=()
	while [ $# -gt 0 ] && [ "$1" != "--" ]; do words+=("$1"); shift; done
	[ $# -gt 0 ] && shift
	local db=(--db-path="$(topath "$dir/db")")
	[ "$DATA_MODE" = "yes" ] && db+=(--data="$(topath "$dir/data")")
	run "$log" "$IBCMD" infobase "${words[@]}" "${db[@]}" "$@"
}

format_of() {
	grep -o 'version="2\.[0-9]*"' "$1" 2>/dev/null | head -1 | grep -o '2\.[0-9]*'
}

fmt_ge() {
	[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -1)" = "$2" ]
}

# Собрать .epf/.erf из исходника <каталог>/<каталог>.xml текущим ibcmd: build_external <ИБ> <исходник> <куда>
build_external() {
	local ib=$1 src=$2 dst=$3 d name root ext
	[ -d "$src" ] || { echo "ERROR: нет исходника $src"; return 1; }
	mkdir -p "$dst"
	for d in "$src"/*/; do
		name=$(basename "$d")
		root="$d$name.xml"
		[ -f "$root" ] || continue
		if grep -q '<ExternalReport ' "$root"; then ext=erf; else ext=epf; fi
		ibc "build-$name.log" "$ib" config import -- --out="$(topath "$dst/$name.$ext")" "$(topath "$root")" \
			|| { show_log "build-$name.log"; return 1; }
	done
}

if [ "$MODE" = "build-external" ]; then
	[ -n "${EXT_BIN:-}" ] || die "задайте EXT_BIN - каталог для .epf/.erf"
	ib_create builder || { show_log create-builder.log; die "не создаётся ИБ"; }
	build_external builder "$EXT_FULL_SRC" "$EXT_BIN" || die "платформа $PLATFORM не собрала внешние объекты"
	ls -l "$EXT_BIN"
	exit 0
fi
[ "$MODE" = "dump" ] || [ "$MODE" = "nodes" ] || die "неизвестная команда: $MODE"

echo "Платформа $PLATFORM: $IBCMD"
SUMMARY=()
note() { SUMMARY+=("$*"); echo "$*"; }
STATUS=0
TAKEN=0

# ---------- 1. Пустая ИБ: формат и конфигурация «по умолчанию» ----------
ib_create empty || { show_log create-empty.log; die "ibcmd не создаёт ИБ"; }
ibc export-empty.log empty config export -- "$(topath "$WORK/x/empty")" \
	|| { show_log export-empty.log; die "не выгружается конфигурация пустой ИБ"; }
FORMAT=$(format_of "$WORK/x/empty/Configuration.xml")
[ -n "$FORMAT" ] || die "не удалось определить формат выгрузки"
DEST="$OUT/$FORMAT"
if [ "$MODE" = "nodes" ]; then
	mkdir -p "$DEST"
	note "Платформа $PLATFORM, формат выгрузки $FORMAT: только cf-object-nodes"
else
	rm -rf "$DEST"
	mkdir -p "$DEST"
	note "Платформа $PLATFORM, формат выгрузки $FORMAT"
	cp -r "$WORK/x/empty" "$DEST/cf-empty-infobase"
	rm -f "$DEST/cf-empty-infobase/ConfigDumpInfo.xml"
	note "cf-empty-infobase: снят (infobase create --locale=ru, config export)"
	TAKEN=$((TAKEN + 1))
fi

HAS_NOCHECK=""
has_help '--base-dir=' && has_help '--no-check' && HAS_NOCHECK=1

# ---------- 2. Голые объекты ----------
# Маршрут «импорт + срезка»: семя с минимальными зависимостями проходит обычный import с проверкой,
# вспомогательные объекты и добавленные свойства после выгрузки убираются.
route_strip() { # route_strip <метка> <исключить виды> [ключи assemble...]
	local tag=$1 exclude=$2
	shift 2
	snap assemble --seed "$(topath "$HERE/seed")" --deps "$(topath "$HERE/seed-deps")" --with-deps \
		--format "$FORMAT" --exclude "$exclude" "$@" \
		--out "$(topath "$WORK/seed-$tag")" --manifest "$(topath "$WORK/seed-$tag.json")" || return 2
	ib_create "$tag" || { show_log "create-$tag.log"; return 2; }
	ibc "import-$tag.log" "$tag" config import -- "$(topath "$WORK/seed-$tag")" || return 1
	ibc "export-$tag.log" "$tag" config export -- "$(topath "$WORK/x/$tag")" || { show_log "export-$tag.log"; return 2; }
}

# Маршрут без проверки: конфигурация с языком обычным import, затем все объекты import files --no-check.
route_nocheck() { # route_nocheck <метка> <куда разложить> [ключи assemble...]
	local tag=$1 dest=$2 files=()
	shift 2
	snap assemble --seed "$(topath "$HERE/seed")" --deps "$(topath "$HERE/seed-deps")" "$@" \
		--format "$FORMAT" --out "$(topath "$WORK/seed-$tag")" --manifest "$(topath "$WORK/seed-$tag.json")" || return 1
	snap prephase --src "$(topath "$WORK/seed-$tag")" --pre "$(topath "$WORK/pre-$tag")" \
		--list "$(topath "$WORK/files-$tag.txt")" || return 1
	mapfile -t files < <(tr -d '\r' <"$WORK/files-$tag.txt")
	ib_create "$tag" || { show_log "create-$tag.log"; return 1; }
	ibc "import0-$tag.log" "$tag" config import -- "$(topath "$WORK/pre-$tag")" || { show_log "import0-$tag.log"; return 1; }
	ibc "import-$tag.log" "$tag" config import files -- --base-dir="$(topath "$WORK/seed-$tag")" --no-check "${files[@]}" \
		|| { show_log "import-$tag.log"; return 1; }
	ibc "export-$tag.log" "$tag" config export -- "$(topath "$WORK/x/$tag")" || { show_log "export-$tag.log"; return 1; }
	snap finish --export "$(topath "$WORK/x/$tag")" --manifest "$(topath "$WORK/seed-$tag.json")" \
		--dest "$(topath "$dest")" --route "import files --no-check (без проверки конфигурации)"
}

# Узлы объектов: владельцы из seed/ (с зависимостями из seed-deps/), каждому по узлу каждого вида,
# как их пишет md-sparrow; остальные свойства узлов дописывает платформа. Маршрут тот же, что
# у голых объектов. В наборе только объекты: конфигурация и язык - как в cf-bare-objects.
NODES="$DEST/cf-object-nodes"
only_objects() {
	rm -rf "$1/Configuration.xml" "$1/Languages"
}

# Внешние отчёт и обработка с теми же узлами: семя из seed-external, сборка .epf/.erf и разборка
# через ibcmd (проверены с 8.3.23). Кладутся в ExternalReports/ и ExternalDataProcessors/ набора.
snap_external_nodes() {
	local src name ext dir bin tmp ok=1
	if ! fmt_ge "$PLATFORM" 8.3.23 || ! has_help '--file='; then
		note "  внешние отчёт и обработка: пропущены - сборка и разборка через ibcmd нужна 8.3.23+, а здесь $PLATFORM"
		return
	fi
	snap external-nodes --seed "$(topath "$HERE/seed-external")" --out "$(topath "$WORK/seed-ext-nodes")" || ok=""
	mkdir -p "$WORK/x/ext-nodes"
	for src in "$WORK/seed-ext-nodes"/*.xml; do
		[ -n "$ok" ] || break
		name=$(basename "$src" .xml)
		if grep -q '<ExternalReport ' "$src"; then ext=erf dir=ExternalReports; else ext=epf dir=ExternalDataProcessors; fi
		bin="$WORK/x/ext-nodes/$name.$ext"
		tmp="$WORK/x/ext-nodes/$name"
		mkdir -p "$tmp" "$NODES/$dir"
		if ibc "ext-nodes-$name.log" empty config import -- --out="$(topath "$bin")" "$(topath "$src")" \
			&& ibc "ext-nodes-export-$name.log" empty config export -- --file="$(topath "$bin")" "$(topath "$tmp")" \
			&& [ -f "$tmp/$name.xml" ]; then
			mv "$tmp/$name.xml" "$NODES/$dir/$name.xml"
		else
			show_log "ext-nodes-$name.log"
			ok=""
		fi
	done
	if [ -n "$ok" ]; then
		note "  внешние отчёт и обработка: сняты (import --out из seed-external с узлами, export --file)"
	else
		note "  внешние отчёт и обработка: НЕ сняты"
		STATUS=3
	fi
}

snap_nodes() {
	local rc report cmp
	route_strip nodes "" --nodes
	rc=$?
	if [ $rc -eq 0 ]; then
		report=$(snap finish --export "$(topath "$WORK/x/nodes")" --manifest "$(topath "$WORK/seed-nodes.json")" \
			--dest "$(topath "$NODES")") || { echo "$report"; note "cf-object-nodes: НЕ снят - не удалось разложить выгрузку"; STATUS=3; return; }
		note "cf-object-nodes: семя принято обычным import с проверкой"
	elif [ $rc -eq 1 ] && [ -n "$HAS_NOCHECK" ]; then
		show_log import-nodes.log 40
		report=$(route_nocheck nodes-nocheck "$NODES" --nodes) \
			|| { echo "$report"; note "cf-object-nodes: НЕ снят - платформа отвергла семя узлов"; STATUS=3; return; }
		note "cf-object-nodes: обычный import семя не принял, снят маршрутом import files --no-check"
	else
		[ $rc -eq 1 ] && show_log import-nodes.log 40
		note "cf-object-nodes: НЕ снят - платформа отвергла семя узлов (журналы: $LOGS)"
		STATUS=3
		return
	fi
	only_objects "$NODES"
	note "$report"
	TAKEN=$((TAKEN + 1))
	if [ $rc -eq 0 ] && [ -n "$HAS_NOCHECK" ] && [ "$VERIFY_ROUTES" = "1" ]; then
		if report=$(route_nocheck nodes-verify "$WORK/verify-nodes" --nodes); then
			only_objects "$WORK/verify-nodes"
			if cmp=$(snap compare "$(topath "$NODES")" "$(topath "$WORK/verify-nodes")"); then
				note "  сверка с маршрутом import files --no-check: побайтно совпало ($(tail -1 <<<"$cmp"))"
			else
				note "  ERROR: сверка с маршрутом import files --no-check не сошлась:"
				note "$cmp"
				STATUS=3
			fi
		else
			echo "$report"
			note "  сверка с маршрутом --no-check не выполнена: маршрут не прошёл"
		fi
	fi
	# внешние - после сверки: во втором маршруте их нет
	snap_external_nodes
}

if [ "$MODE" = "nodes" ]; then
	snap_nodes
	printf '%s\n' "${SUMMARY[@]}" >"$OUT/summary-$PLATFORM-nodes.txt"
	echo "Сводка: $OUT/summary-$PLATFORM-nodes.txt"
	[ "$TAKEN" -gt 0 ] || die "ничего не снято"
	exit $STATUS
fi

BARE="$DEST/cf-bare-objects"
route_strip bare ""
RC=$?
if [ $RC -eq 0 ]; then
	REPORT=$(snap finish --export "$(topath "$WORK/x/bare")" --manifest "$(topath "$WORK/seed-bare.json")" \
		--dest "$(topath "$BARE")") || { echo "$REPORT"; die "не удалось разложить выгрузку голых объектов"; }
	note "cf-bare-objects: семя принято обычным import с проверкой"
	note "$REPORT"
	TAKEN=$((TAKEN + 1))
	if [ -n "$HAS_NOCHECK" ] && [ "$VERIFY_ROUTES" = "1" ]; then
		if REPORT=$(route_nocheck verify "$WORK/verify"); then
			if CMP=$(snap compare "$(topath "$BARE")" "$(topath "$WORK/verify")"); then
				note "  сверка с маршрутом import files --no-check: побайтно совпало ($(tail -1 <<<"$CMP"))"
			else
				note "  ERROR: сверка с маршрутом import files --no-check не сошлась:"
				note "$CMP"
				STATUS=3
			fi
		else
			echo "$REPORT"
			note "  сверка с маршрутом --no-check не выполнена: маршрут не прошёл"
		fi
	fi
elif [ $RC -eq 1 ]; then
	show_log import-bare.log 40
	FAILED=$(snap failed --log "$(topath "$LOGS/import-bare.log")" --manifest "$(topath "$WORK/seed-bare.json")")
	note "cf-bare-objects: платформа отвергла семя обычным import (виды в ошибках: ${FAILED:-не определены})"
	if [ -n "$HAS_NOCHECK" ]; then
		REPORT=$(route_nocheck nocheck "$BARE") || { echo "$REPORT"; die "платформа отвергла семя и маршрутом import files --no-check"; }
		note "cf-bare-objects: все виды сняты запасным маршрутом import files --no-check"
		note "$REPORT"
		TAKEN=$((TAKEN + 1))
	elif [ -n "$FAILED" ]; then
		route_strip bare2 "$FAILED" \
			|| { show_log import-bare2.log 40; die "платформа отвергла семя и без видов $FAILED"; }
		REPORT=$(snap finish --export "$(topath "$WORK/x/bare2")" --manifest "$(topath "$WORK/seed-bare2.json")" \
			--dest "$(topath "$BARE")") || { echo "$REPORT"; die "не удалось разложить выгрузку голых объектов"; }
		note "cf-bare-objects: в ibcmd нет import files --no-check, виды $FAILED не сняты"
		note "$REPORT"
		TAKEN=$((TAKEN + 1))
		STATUS=3
	else
		die "платформа отвергла семя, а import files --no-check в этой версии ibcmd нет"
	fi
else
	die "маршрут импорта голых объектов не выполнен (журналы: $LOGS)"
fi

# ---------- 2б. Узлы объектов ----------
snap_nodes

# ---------- 3. Пустое расширение ----------
CFE_TMP="$WORK/x/cfe"
cfe_export() { # cfe_export <журнал>
	rm -rf "$CFE_TMP"
	ibc "$1" empty config export -- --extension="$EXT_NAME" "$(topath "$CFE_TMP")"
}
CFE_HOW=""
CFE_CREATE=""
if has_help '--name-prefix='; then
	CFE_CREATE=1
	# 8.3.20 создаёт расширение, но отменяет обновление конфигурации БД и возвращает ненулевой код:
	# расширение в ИБ при этом есть, поэтому выгрузку пробуем при любом коде.
	ibc cfe-create.log empty config extension create -- --name="$EXT_NAME" --name-prefix=пр --purpose=add-on
	CREATE_RC=$?
	if cfe_export cfe-export.log; then
		if [ "$CREATE_RC" -eq 0 ]; then
			CFE_HOW="extension create --purpose=add-on в пустой ИБ, config export --extension"
		else
			CFE_HOW="extension create --purpose=add-on вернул код $CREATE_RC, config export --extension"
		fi
	else
		show_log cfe-create.log
		show_log cfe-export.log
	fi
fi
if [ -z "$CFE_HOW" ]; then
	# Режима расширений в ibcmd нет или он не дал расширения: загружаем расширение из минимального XML.
	if ibc cfe-import.log empty config import -- --extension="$EXT_NAME" "$(topath "$HERE/seed-cfe")" \
		&& cfe_export cfe-export-seed.log; then
		CFE_HOW="запасным путём: config import --extension из seed-cfe, config export --extension"
	else
		show_log cfe-import.log
		[ -f "$LOGS/cfe-export-seed.log" ] && show_log cfe-export-seed.log
		rm -rf "$CFE_TMP"
	fi
fi
if [ -n "$CFE_HOW" ]; then
	note "cfe-empty: снят ($CFE_HOW)"
elif [ -n "$CFE_CREATE" ]; then
	note "cfe-empty: НЕ снят - ни extension create, ни загрузка расширения из seed-cfe не дали выгрузки"
	STATUS=3
else
	note "cfe-empty: НЕ снят - в ibcmd нет extension create, а загрузка расширения из XML не прошла"
	STATUS=3
fi
if [ -f "$CFE_TMP/Configuration.xml" ]; then
	cp -r "$CFE_TMP" "$DEST/cfe-empty"
	rm -f "$DEST/cfe-empty/ConfigDumpInfo.xml"
	TAKEN=$((TAKEN + 1))
fi

# ---------- 4. Внешние отчёт и обработка ----------
# Разобрать .epf/.erf в раскладку эталона: <каталог>/<каталог>.xml и <каталог>/<каталог>/...
unpack_external() { # unpack_external <файл> <каталог назначения> <имя каталога>
	local file=$1 dst=$2 dir=$3 tmp stem log
	# Каталог выгрузки должен существовать: иначе ibcmd пишет файл <путь>.xml рядом с ним.
	tmp=$(mktemp -d "$WORK/x/unpack.XXXXXX")
	log="unpack-$(basename "$dst")-$dir.log"
	ibc "$log" empty config export -- --file="$(topath "$file")" "$(topath "$tmp")" \
		|| { show_log "$log"; return 1; }
	stem=$(cd "$tmp" && ls -- *.xml 2>/dev/null | head -1)
	[ -n "$stem" ] || { echo "ERROR: export --file не выгрузил $file"; return 1; }
	stem=${stem%.xml}
	mkdir -p "$dst/$dir"
	mv "$tmp/$stem.xml" "$dst/$dir/$dir.xml"
	[ -d "$tmp/$stem" ] && mv "$tmp/$stem" "$dst/$dir/$dir"
	return 0
}

# Сборка и разборка .epf/.erf проверены с 8.3.23; у старших версий смотрим ещё справку.
if ! fmt_ge "$PLATFORM" 8.3.23 || ! has_help '--file='; then
	note "external-files: пропущены - сборка и разборка внешних объектов через ibcmd нужна 8.3.23+, а здесь $PLATFORM"
else
	EXT_OK=1
	mkdir -p "$WORK/x/ext-empty"
	for SRC in "$HERE"/seed-external/*.xml; do
		NAME=$(basename "$SRC" .xml)
		if grep -q '<ExternalReport ' "$SRC"; then EXT=erf; else EXT=epf; fi
		BINFILE="$WORK/x/ext-empty/$NAME.$EXT"
		if ibc "ext-$NAME.log" empty config import -- --out="$(topath "$BINFILE")" "$(topath "$SRC")" \
			&& unpack_external "$BINFILE" "$DEST/external-files/empty" "$NAME"; then
			:
		else
			show_log "ext-$NAME.log"
			EXT_OK=""
		fi
	done
	if [ -n "$EXT_OK" ]; then
		note "external-files/empty: снят (import --out из seed-external, export --file)"
		TAKEN=$((TAKEN + 1))
	else
		note "external-files/empty: НЕ снят"
		STATUS=3
	fi

	# empty-full-objects: исходник в формате 2.20 читают только платформы 8.3.27+, а .epf/.erf
	# переносимы между версиями, поэтому старые платформы разбирают файлы, собранные новой.
	FULL_BIN=""
	FULL_ERR=""
	FULL_WHY="формат $FORMAT не читает исходник 2.20, задайте EXT_BIN или BUILDER_BIN"
	if [ -n "${EXT_BIN:-}" ] && ls "$EXT_BIN"/*.e?f >/dev/null 2>&1; then
		FULL_BIN="$EXT_BIN"
		FULL_HOW="готовые .epf/.erf из EXT_BIN"
	elif fmt_ge "$FORMAT" 2.20; then
		FULL_WHY="эта платформа не собрала .epf/.erf из $EXT_FULL_SRC"
		FULL_ERR=1
		build_external empty "$EXT_FULL_SRC" "$WORK/x/ext-full-bin" && FULL_BIN="$WORK/x/ext-full-bin"
		FULL_HOW="собраны этой же платформой из EXT_FULL_SRC"
	elif [ -n "${BUILDER_BIN:-}" ]; then
		BUILDER=$(find_ibcmd "$BUILDER_BIN")
		FULL_WHY="в BUILDER_BIN нет ibcmd"
		FULL_ERR=1
		if [ -n "$BUILDER" ]; then
			FULL_WHY="платформа из BUILDER_BIN не собрала .epf/.erf из $EXT_FULL_SRC"
			SAVED=("$IBCMD" "$DATA_MODE")
			IBCMD=$BUILDER
			DATA_MODE=""
			ib_create builder && build_external builder "$EXT_FULL_SRC" "$WORK/x/ext-full-bin" \
				&& FULL_BIN="$WORK/x/ext-full-bin"
			IBCMD=${SAVED[0]}
			DATA_MODE=${SAVED[1]}
			FULL_HOW="собраны платформой $("$BUILDER" --version 2>/dev/null | tr -d '\r' | head -1) из EXT_FULL_SRC"
		fi
	fi
	if [ -z "$FULL_BIN" ] && [ -n "$FULL_ERR" ]; then
		note "external-files/empty-full-objects: НЕ сняты - $FULL_WHY"
		STATUS=3
	elif [ -z "$FULL_BIN" ]; then
		note "external-files/empty-full-objects: пропущены - $FULL_WHY"
	else
		FULL_OK=1
		for FILE in "$FULL_BIN"/*.e?f; do
			NAME=$(basename "$FILE")
			NAME=${NAME%.*}
			unpack_external "$FILE" "$DEST/external-files/empty-full-objects" "$NAME" || FULL_OK=""
		done
		if [ -n "$FULL_OK" ]; then
			note "external-files/empty-full-objects: снят (export --file; $FULL_HOW)"
			TAKEN=$((TAKEN + 1))
		else
			note "external-files/empty-full-objects: НЕ снят"
			STATUS=3
		fi
	fi
fi

printf '%s\n' "${SUMMARY[@]}" >"$OUT/summary-$PLATFORM.txt"
echo "Сводка: $OUT/summary-$PLATFORM.txt"
[ "$TAKEN" -gt 0 ] || die "ничего не снято"
exit $STATUS
