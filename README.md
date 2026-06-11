# md-sparrow

[![OpenYellow](https://openyellow.openintegrations.dev/data/badges/1113279075.png)](https://openyellow.org/grid?filter=top&repo=1113279075)
[![telegram chat](resources/badges/telegram-chat.png)](https://t.me/wonder_yellow)
[![Ask Devin](resources/badges/deepwiki-badge.png)](https://deepwiki.com/yellow-hammer/md-sparrow)

Java-библиотека для чтения/записи XML метаданных 1С (`MetaDataObject`) по XSD из [namespace-forest](https://github.com/yellow-hammer/namespace-forest).

## Что важно перед стартом

- Нужен JDK 21.
- Нужны submodule: `resources/namespace-forest/` (XSD), `fixtures/ssl31/` (типовая конфигурация для тестов чтения) и `fixtures/samples-1c-platform/` (эталоны для scaffold). Команда: `git submodule update --init --recursive`.

## API

- **`io.github.yellowhammer.designerxml.DesignerXml`** — чтение/запись JAXB `MetaDataObject` по [`SchemaVersion`](src/main/java/io/github/yellowhammer/designerxml/SchemaVersion.java).
- **`io.github.yellowhammer.designerxml.XmlValidator`** — валидация XML по XSD из submodule `resources/namespace-forest`.
- **`io.github.yellowhammer.designerxml.cf.MdObjectAdd`** — создание новых объектов метаданных в `src/cf` из golden-эталонов нужной версии (см. [docs/scaffold-golden.md](docs/scaffold-golden.md)).
- **`io.github.yellowhammer.designerxml.cf.CatalogFormEdit`** — чтение/запись полей формы справочника (имя, синоним ru, комментарий) через JAXB.
- **`io.github.yellowhammer.designerxml.cf.MdObjectChildMutations`** — CRUD мутации дочерних узлов объекта (реквизиты, ТЧ, реквизиты ТЧ).

## CLI (`DesignerXmlCli`)

Основные команды CLI:

- `validate` — проверка XML по XSD (`-v V2_10`…`V2_21`, путь к XSD root).
- `round-trip` — проверка JAXB: прочитать и записать файл.
- `transcode` — перекодировать XML метаданных между версиями формата (понижение/повышение).
- `init-empty-cf` — инициализация пустой выгрузки в `src/cf`.
- `project-metadata-tree` — JSON-дерево метаданных по корню проекта (узлы с синонимами и родительскими подсистемами).
- `cf-md-graph` — граф метаданных проекта (узлы + типизированные связи) в JSON; используется расширением VS Code для ER-диаграмм.
- `add-md-object` — создание объекта метаданных (`--type`: `CATALOG`, `DOCUMENT`, `ENUM`, … — 19 видов; для каталога доступны `--synonym-ru` / `--synonym-empty`).
- `external-artifact-add` / `external-artifact-properties-get` / `…-set` / `…-rename` / `…-delete` / `…-duplicate` — отдельные внешние отчёты и обработки (`src/erf`, `src/epf`).
- `cf-list-catalogs` — JSON-массив имён справочников из `ChildObjects`.
- `cf-list-child-objects` — список имён дочерних объектов по `--tag`.
- `cf-catalog-form-get` / `cf-catalog-form-set` — чтение/запись формы справочника.
- `cf-md-object-get` / `cf-md-object-set` — чтение/запись DTO свойств объекта метаданных (имя, синоним, комментарий и т. д.).
- `cf-md-object-structure-get` — чтение структуры объекта (секции, ТЧ, вложенные узлы).
- `cf-md-object-rename` / `cf-md-object-delete` / `cf-md-object-duplicate` — переименование/удаление/копирование объекта метаданных.
- `cf-md-attribute-*`, `cf-md-tabular-section-*`, `cf-md-tabular-attribute-*` — CRUD реквизитов и табличных частей (`add`/`rename`/`delete`/`duplicate`, см. `MdObjectChildMutations`).
- `cf-configuration-properties-get` / `cf-configuration-properties-set` — чтение/запись свойств конфигурации (`Configuration.xml`).

Интеграция добавления справочника: `io.github.yellowhammer.designerxml.cf.AddCatalogIntegrationTest` (`MdObjectAddType.CATALOG`; `Configuration.xml` + один `Catalogs/*.xml` из submodule `fixtures/ssl31`, временный `src/cf`).

## Для разработчиков

- [CONTRIBUTING.md](CONTRIBUTING.md) — как внести вклад (сборка, тесты, схемы, выпуск JAR).
- [docs/cf-layout.md](docs/cf-layout.md) — раскладка `src/cf` и порядок `ChildObjects`.
- [docs/cf-md-object.md](docs/cf-md-object.md) — контракт свойств объектов метаданных (CLI cf-md-object, DTO, матрица типов).
- [docs/scaffold-golden.md](docs/scaffold-golden.md) — архитектура scaffold по golden-эталонам.
- **`.cursor/rules/`** — правила Cursor для агента (контекст JAXB, submodules, стиль).
- `.github/workflows/release.yml` — выкладывает `md-sparrow-*-all.jar` в GitHub Releases при push тега `v*` или ручном запуске (workflow_dispatch с вводом версии).

## Лицензия

LGPL-3.0-or-later. Подробности см. в файле [LICENCE](LICENCE).

## Автор

Ivan Karlo (<i.karlo@outlook.com>)

При желании, отблагодарить автора можно по ссылке:

- [Boosty](https://boosty.to/1carlo/donate)
- [Чаевые](https://pay.cloudtips.ru/p/d752cb43)
