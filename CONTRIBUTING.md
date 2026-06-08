# Участие в разработке

## Cursor / ИИ

Правила для агента и контекст репозитория: **`.cursor/rules/*.mdc`** (архитектура, submodules, стиль Java, XJC, политика для `fixtures/ssl31`).

## Сборка

```bash
./gradlew build
./gradlew test
./gradlew shadowJar   # fat JAR → build/libs/md-sparrow-*-all.jar (VS Code / скрипты)
./gradlew javadoc   # HTML → build/docs/javadoc/index.html (в VS Code: задача «javadoc»)
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

## Интеграция в IDE (отдельная задача)

Имеет смысл вызывать библиотеку из расширения **1C: Platform Tools** ([vscode-1c-platform-tools](https://github.com/yellow-hammer/vscode-1c-platform-tools)): JDK 21, артефакт `./gradlew shadowJar` → `build/libs/md-sparrow-*-all.jar`, подпроцесс `java -jar …` с подкомандами CLI (`add-md-object --type CATALOG` и др.). Дублировать отдельное мини-расширение под это не обязательно — достаточно настроек путей и команды в существующем дереве инструментов.
