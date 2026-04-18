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

По умолчанию корень XSD — `namespace-forest/` (submodule). Другой путь:

```bash
./gradlew build -Pxsd.root=C:/path/to/namespace-forest
```

Новая версия набора схем — синхронно обновить:

- `src/main/java/io/github/yellowhammer/designerxml/SchemaVersion.java`
- `gradle.properties`
- `xjb/ns/<версия>/`

## Интеграция в IDE (отдельная задача)

Имеет смысл вызывать библиотеку из расширения **1C: Platform Tools** ([vscode-1c-platform-tools](https://github.com/yellow-hammer/vscode-1c-platform-tools)): JDK 21, артефакт `./gradlew shadowJar` → `build/libs/md-sparrow-*-all.jar`, подпроцесс `java -jar …` с подкомандами CLI (`add-catalog` и др.). Дублировать отдельное мини-расширение под это не обязательно — достаточно настроек путей и команды в существующем дереве инструментов.
