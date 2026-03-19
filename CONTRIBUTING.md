# Участие в разработке

## Сборка

```bash
./gradlew build
./gradlew test
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
