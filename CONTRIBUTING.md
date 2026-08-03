# Участие в разработке

## Cursor / ИИ

Правила для агента и контекст репозитория: **`.cursor/rules/*.mdc`** (архитектура, submodules, стиль Java, XJC, политика для `fixtures/ssl31`).

## Сборка

```bash
./gradlew build
./gradlew test
./gradlew shadowJar   # fat JAR → build/libs/md-sparrow-*-all.jar
./gradlew javadoc     # HTML → build/docs/javadoc/index.html
```

Проверка лицензионных заголовков входит в `./gradlew check` (задача `license`).

## Схемы (`xsd.root`)

По умолчанию корень XSD — `resources/namespace-forest/` (submodule). Другой путь:

```bash
./gradlew build -Pxsd.root=C:/path/to/namespace-forest
```

Новая версия набора схем:

- добавить каталог `schemas/<версия>/` в submodule `resources/namespace-forest` и обновить указатель submodule;
- добавить константу в `src/main/java/io/github/yellowhammer/designerxml/SchemaVersion.java`.

`bindings.xjb`/`catalog.xml` для XJC и карта import'ов валидатора строятся автоматически
(см. `build.gradle.kts` и `XmlValidator`) — править их вручную не нужно.

## Проверка своей сборки

`./gradlew shadowJar` собирает fat JAR в `build/libs/`. Если поведение CLI расходится с исходниками, а тесты зелёные — пересоберите с `--rerun-tasks`: причина обычно в протухшем build cache.

Программы, вызывающие библиотеку подпроцессом, обычно берут выпущенный JAR из GitHub Releases, поэтому для проверки своей сборки в такой программе нужно указать ей путь к локальному файлу и отключить автообновление, если оно есть.

## Новые операции CLI

Изменения и чтение вызывающие программы делают не отдельными подкомандами, а двумя каналами: `apply-mutation` и `read-json` с `--params` — путём к UTF-8 JSON. Причина в `CliParams`: на Windows лаунчер `java.exe` декодирует `argv` через ANSI-кодовую страницу ОС, и кириллические имена и пути превращаются в `?`. Поэтому новая операция заводится полем `op` в этом канале, а одиночная подкоманда остаётся для скриптов и ручного запуска.
