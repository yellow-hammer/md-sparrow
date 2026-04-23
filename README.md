# md-sparrow

Java-библиотека для чтения/записи XML метаданных 1С (`MetaDataObject`) по XSD из [namespace-forest](https://github.com/yellow-hammer/namespace-forest).

## Что важно перед стартом

- Нужен JDK 21.
- Нужны submodule: `namespace-forest/` (XSD) и `fixtures/ssl31/`. Команда: `git submodule update --init --recursive`.

## API

- **`io.github.yellowhammer.designerxml.DesignerXml`** — чтение/запись JAXB `MetaDataObject` по [`SchemaVersion`](src/main/java/io/github/yellowhammer/designerxml/SchemaVersion.java).
- **`io.github.yellowhammer.designerxml.XmlValidator`** — валидация XML по XSD из submodule `namespace-forest`.
- **`io.github.yellowhammer.designerxml.cf.MdObjectAdd`** — создание новых объектов метаданных в `src/cf` по XSD/JAXB (включая `--type CATALOG`, см. [docs/cf-layout.md](docs/cf-layout.md)).
- **`io.github.yellowhammer.designerxml.cf.CatalogFormEdit`** — чтение/запись полей формы справочника (имя, синоним ru, комментарий) через JAXB.
- **`io.github.yellowhammer.designerxml.cf.MdObjectChildMutations`** — CRUD мутации дочерних узлов объекта (реквизиты, ТЧ, реквизиты ТЧ).

## CLI (`DesignerXmlCli`)

Основные команды CLI:

- `validate` — проверка XML по XSD (`-v V2_20` / `V2_21`, путь к XSD root).
- `round-trip` — проверка JAXB: прочитать и записать файл.
- `init-empty-cf` — инициализация пустой выгрузки в `src/cf`.
- `project-metadata-tree` — JSON-дерево метаданных по корню проекта (узлы с синонимами и родительскими подсистемами).
- `cf-md-graph` — граф метаданных проекта (узлы + типизированные связи) в JSON; используется расширением VS Code для ER-диаграмм.
- `add-md-object` — создание объекта метаданных поддержанного типа (`--type`, включая `CATALOG`; для каталога доступны `--synonym-ru` / `--synonym-empty`).
- `cf-list-catalogs` — JSON-массив имён справочников из `ChildObjects`.
- `cf-list-child-objects` — список имён дочерних объектов по `--tag`.
- `cf-catalog-form-get` / `cf-catalog-form-set` — чтение/запись формы справочника.
- `cf-md-object-get` — чтение DTO свойств объекта метаданных (имя, синоним, комментарий и т. д.).
- `cf-md-object-structure-get` — чтение структуры объекта (секции, ТЧ, вложенные узлы).
- `cf-configuration-properties-get` / `cf-configuration-properties-set` — чтение/запись свойств конфигурации (`Configuration.xml`).

Интеграция добавления справочника: `io.github.yellowhammer.designerxml.cf.AddCatalogIntegrationTest` (`MdObjectAddType.CATALOG`; `Configuration.xml` + один `Catalogs/*.xml` из submodule `fixtures/ssl31`, временный `src/cf`).

## Для разработчиков

- [CONTRIBUTING.md](CONTRIBUTING.md) — как внести вклад (включая сборку, тесты и выпуск JAR).
- **`.cursor/rules/`** — правила Cursor для агента (контекст JAXB, submodules, стиль).
- [docs/release.md](docs/release.md) — как выпустить релиз.
- `.github/workflows/release.yml` — при push тега `v*` в GitHub Releases выкладывается `md-sparrow-*-all.jar`.

## Лицензия

LGPL-3.0-or-later. Подробности см. в файле [LICENCE](LICENCE).

## Автор

Ivan Karlo (<i.karlo@outlook.com>)

При желании, отблагодарить автора можно по ссылке:

- [Boosty](https://boosty.to/1carlo/donate)
- [Чаевые](https://pay.cloudtips.ru/p/d752cb43)
