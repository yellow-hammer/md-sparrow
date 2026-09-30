#!/usr/bin/env bash
# Импорт снятых эталонов в проекты 1C:EDT через 1cedtcli: без лицензии и сети.
#
# Для каждого формата <SNAPSHOTS>/<формат> создаёт в <OUT>/<формат>/ проекты:
#   ЭталонСемя                    конфигурация из cf-bare-objects
#   ЭталонСемя.ПустоеРасширение   расширение из cfe-empty (базовый проект ЭталонСемя)
#   <Имя>                         внешние отчёт и обработка из external-files/empty/<Имя>
# Каждый import - отдельный запуск 1cedtcli с таймаутом: в одном сеансе EDT 2026.1 команда
# после import может зависнуть. Рабочая область своя на каждый формат: имена проектов совпадают.
#
# EDT 2026.1 требует Java 17: с другой версией (например 21) 1cedtcli показывает окно
# «Неподдерживаемая Java» и ждёт ответа. Ключ -vm launcher 1cedtcli не принимает
# («Unrecognized option: -vm»), JAVA_HOME не читает: Java он берёт из строки -vm в 1cedt.ini
# (каталог bin JDK), иначе из <каталог EDT>/jre, иначе java из PATH. Скрипт находит Java тем же
# порядком и до импорта проверяет, что это 17.
# Дисплей не нужен: без X 1cedtcli работает, если стоит libgtk-3-0. На рабочей машине
# с графическим сеансом 1cedtcli поднимает окно EDT: скрипт рассчитан на CI.
#
# Переменные окружения:
#   EDTCLI       путь к 1cedtcli (по умолчанию ищется в /opt/1C)
#   SNAPSHOTS    корень эталонов (по умолчанию tools/golden-snapshots/out)
#   OUT          корень проектов (по умолчанию tools/golden-snapshots/out-edt)
#   FORMATS      форматы через пробел (по умолчанию все каталоги SNAPSHOTS)
#   WORK         рабочий каталог для рабочих областей EDT (по умолчанию временный)
#   EDT_TIMEOUT  секунд на один запуск 1cedtcli (по умолчанию 1800)
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
SNAPSHOTS="${SNAPSHOTS:-$HERE/out}"
OUT="${OUT:-$HERE/out-edt}"
EDT_TIMEOUT="${EDT_TIMEOUT:-1800}"
BASE=ЭталонСемя

die() {
	echo "ERROR: $*" >&2
	exit 1
}

topath() {
	if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}

EDTCLI="${EDTCLI:-$(find /opt/1C -name 1cedtcli -type f 2>/dev/null | sort -V | tail -1)}"
[ -n "$EDTCLI" ] && [ -f "$EDTCLI" ] || die "1cedtcli не найден, задайте EDTCLI"
[ -d "$SNAPSHOTS" ] || die "нет каталога эталонов $SNAPSHOTS"

# Java, которую возьмёт launcher: строка после -vm в 1cedt.ini (до -vmargs; java, каталог bin или
# корень JDK, относительный путь - от каталога EDT), иначе <каталог EDT>/jre, иначе java из PATH
EDT_DIR=$(dirname "$EDTCLI")
EDT_INI="$EDT_DIR/1cedt.ini"
INI_VM=""
[ -f "$EDT_INI" ] && INI_VM=$(tr -d '\r' <"$EDT_INI" | awk '/^-vmargs/ { exit } f { print; exit } $0 == "-vm" { f = 1 }')
if [ -n "$INI_VM" ]; then
	case "$INI_VM" in /* | ?:*) ;; *) INI_VM="$EDT_DIR/$INI_VM" ;; esac
	EDT_JAVA=$INI_VM
	[ -d "$INI_VM" ] && EDT_JAVA="$INI_VM/java"
	[ -d "$INI_VM/bin" ] && EDT_JAVA="$INI_VM/bin/java"
	JAVA_FROM="-vm в $EDT_INI"
elif [ -d "$EDT_DIR/jre" ]; then
	EDT_JAVA="$EDT_DIR/jre/bin/java"
	JAVA_FROM="$EDT_DIR/jre"
else
	EDT_JAVA=$(command -v java || true)
	JAVA_FROM="PATH"
fi
JAVA_HINT="задайте в $EDT_INI до -vmargs строку -vm и следом каталог bin JDK 17"
[ -n "$EDT_JAVA" ] && [ -x "$EDT_JAVA" ] ||
	die "1cedtcli не найдёт Java (${JAVA_FROM}: ${EDT_JAVA:-java нет}): $JAVA_HINT"
JAVA_MAJOR=$("$EDT_JAVA" -version 2>&1 | grep -o 'version "[0-9]*' | grep -o '[0-9]*$')
[ "$JAVA_MAJOR" = "17" ] ||
	die "EDT нужна Java 17, а 1cedtcli возьмёт $EDT_JAVA (${JAVA_FROM}) версии ${JAVA_MAJOR:-неизвестной}: $JAVA_HINT"
echo "Java для EDT: $EDT_JAVA (${JAVA_FROM})"

if [ -z "${WORK:-}" ]; then
	WORK=$(mktemp -d)
	trap 'rm -rf "$WORK"' EXIT
fi
mkdir -p "$WORK" "$OUT"

FORMATS="${FORMATS:-$(cd "$SNAPSHOTS" && ls -d 2.* 2>/dev/null | sort -V)}"
[ -n "$FORMATS" ] || die "в $SNAPSHOTS нет каталогов форматов"

TIMEOUT=()
command -v timeout >/dev/null 2>&1 && TIMEOUT=(timeout $((EDT_TIMEOUT + 120)))

# edt_import <журнал> <рабочая область> <аргументы import...>
edt_import() {
	local log="$WORK/$1" ws=$2
	shift 2
	"${TIMEOUT[@]}" "$EDTCLI" -data "$(topath "$ws")" -timeout "$EDT_TIMEOUT" -command import "$@" \
		</dev/null >"$log" 2>&1
	local rc=$?
	if [ $rc -ne 0 ]; then
		echo "ERROR: 1cedtcli import завершился с кодом $rc, журнал $log:"
		tail -30 "$log"
	fi
	return $rc
}

FAILED=0
DONE=0
for FMT in $FORMATS; do
	SRC="$SNAPSHOTS/$FMT"
	DST="$OUT/$FMT"
	WS="$WORK/ws-$FMT"
	rm -rf "$DST" "$WS"
	mkdir -p "$DST"
	echo "== формат $FMT"
	if [ -d "$SRC/cf-bare-objects" ]; then
		if edt_import "cf-$FMT.log" "$WS" --configuration-files "$(topath "$SRC/cf-bare-objects")" \
			--project "$(topath "$DST/$BASE")"; then
			echo "   $BASE: $(find "$DST/$BASE/src" -name '*.mdo' | wc -l) файлов .mdo"
			DONE=$((DONE + 1))
		else
			FAILED=$((FAILED + 1))
		fi
	fi
	if [ -d "$SRC/cfe-empty" ] && [ -d "$DST/$BASE" ]; then
		if edt_import "cfe-$FMT.log" "$WS" --base-project-name "$BASE" \
			--configuration-files "$(topath "$SRC/cfe-empty")" --project "$(topath "$DST/$BASE.ПустоеРасширение")"; then
			echo "   $BASE.ПустоеРасширение: импортирован"
			DONE=$((DONE + 1))
		else
			FAILED=$((FAILED + 1))
		fi
	fi
	# Внешние объекты импортируются без базового проекта: с ним EDT 2026.1 не находит открытый проект.
	for EXT in "$SRC"/external-files/empty/*/; do
		[ -d "$EXT" ] || continue
		NAME=$(basename "$EXT")
		if edt_import "ext-$NAME-$FMT.log" "$WS" --configuration-files "$(topath "$EXT")" \
			--project "$(topath "$DST/$NAME")"; then
			echo "   $NAME: импортирован"
			DONE=$((DONE + 1))
		else
			FAILED=$((FAILED + 1))
		fi
	done
done

echo "Импортировано проектов: $DONE, с ошибкой: $FAILED"
[ "$DONE" -gt 0 ] || die "ни один проект не импортирован"
[ "$FAILED" -eq 0 ] || exit 3
