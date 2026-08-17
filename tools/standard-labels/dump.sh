#!/usr/bin/env bash
# Снятие подписей стандартных реквизитов с платформы 1С.
#
# Создаёт файловую ИБ, загружает в неё конфигурацию из src/, публикует HTTP-сервис
# автономным сервером и забирает JSON. Ни конфигуратор, ни лицензия не нужны.
#
# Переменные окружения:
#   BIN    каталог с ibcmd/ibsrv (по умолчанию ищется в /opt/1cv8 и /opt/1C)
#   OUT    каталог для результата (по умолчанию каталог ресурсов библиотеки)
#   PORT   порт автономного сервера (по умолчанию 8314)
#   PROBE  снять заодно диагностику: что платформа отвечает на отдельные выражения
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$HERE/../../src/main/resources/io/github/yellowhammer/designerxml/cf}"
PORT="${PORT:-8314}"

if [ -n "${BIN:-}" ]; then
	IBCMD=$(ls "$BIN"/ibcmd "$BIN"/ibcmd.exe 2>/dev/null | head -1)
	IBSRV=$(ls "$BIN"/ibsrv "$BIN"/ibsrv.exe 2>/dev/null | head -1)
else
	IBCMD=$(find /opt/1cv8 /opt/1C -name 'ibcmd' -type f 2>/dev/null | sort -V | tail -1)
	IBSRV=$(find /opt/1cv8 /opt/1C -name 'ibsrv' -type f 2>/dev/null | sort -V | tail -1)
fi
if [ -z "${IBCMD:-}" ] || [ -z "${IBSRV:-}" ]; then
	echo "ERROR: ibcmd/ibsrv не найдены, задайте BIN"
	exit 1
fi

# На Windows утилиты платформы понимают только пути Windows.
topath() {
	if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/db" "$WORK/data"

DB=$(topath "$WORK/db")
DATA=$(topath "$WORK/data")
SRC=$(topath "$HERE/src")
PUB=$(topath "$HERE/publication.yaml")

"$IBCMD" infobase create --db-path="$DB" --data="$DATA" --locale=ru > "$WORK/create.log" 2>&1 \
	|| { echo "ERROR: создание ИБ"; tail -20 "$WORK/create.log"; exit 1; }
"$IBCMD" infobase config import --db-path="$DB" --data="$DATA" "$SRC" > "$WORK/import.log" 2>&1 \
	|| { echo "ERROR: импорт конфигурации"; tail -30 "$WORK/import.log"; exit 1; }
"$IBCMD" infobase config apply --db-path="$DB" --data="$DATA" --force > "$WORK/apply.log" 2>&1 \
	|| { echo "ERROR: обновление конфигурации базы данных"; tail -30 "$WORK/apply.log"; exit 1; }

"$IBSRV" --db-path="$DB" --data="$DATA" --port="$PORT" --config="$PUB" > "$WORK/ibsrv.log" 2>&1 &
IBSRV_PID=$!

mkdir -p "$OUT"
CODE=000
for _ in $(seq 1 30); do
	CODE=$(curl -s -m 120 -o "$OUT/standard-labels.json" -w '%{http_code}' \
		"http://127.0.0.1:$PORT/hs/labels/dump" 2>/dev/null || echo 000)
	[ "$CODE" != "000" ] && break
	sleep 2
done
if [ -n "${PROBE:-}" ]; then
	curl -s -m 120 -o "$HERE/probe.json" "http://127.0.0.1:$PORT/hs/labels/probe" 2>/dev/null
	echo "Диагностика: $HERE/probe.json"
fi

kill "$IBSRV_PID" >/dev/null 2>&1
command -v taskkill >/dev/null 2>&1 && taskkill //F //IM ibsrv.exe >/dev/null 2>&1

if [ "$CODE" != "200" ]; then
	echo "ERROR: HTTP-сервис вернул $CODE"
	head -c 600 "$OUT/standard-labels.json" 2>/dev/null
	echo
	cat "$WORK/ibsrv.log"
	exit 1
fi

# Порядок ключей у соответствия 1С свой, читать и сверять такой файл неудобно: раскладываем по имени.
PYBIN=""
for CAND in python3 python py; do
	if [ "$("$CAND" -c "print('ok')" 2>/dev/null)" = "ok" ]; then PYBIN="$CAND"; break; fi
done
if [ -n "$PYBIN" ]; then
	"$PYBIN" - "$OUT/standard-labels.json" <<-'PY'
	import json
	import sys

	path = sys.argv[1]
	with open(path, encoding='utf-8-sig') as src:
	    data = json.load(src)
	with open(path, 'w', encoding='utf-8', newline='\n') as dst:
	    json.dump(data, dst, ensure_ascii=False, indent=2, sort_keys=True)
	    dst.write('\n')
	PY
fi

echo "Снято: $OUT/standard-labels.json ($(wc -c < "$OUT/standard-labels.json") байт)"
