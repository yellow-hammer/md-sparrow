# Раскладка каталога конфигурации (`src/cf`)

Структура каталогов `src/cf` согласована с выгрузкой конфигуратора. **Типовая большая** выгрузка для тестов в репозитории — submodule **`fixtures/ssl31`** (не путать с пустой ИБ). **Эталон минимальной** выгрузки — репозиторий **[1c-platform-samples](https://github.com/yellow-hammer/1c-platform-samples)** (`src/cf`); см. `.cursor/rules/empty-configuration-reference.mdc`.

Команда CLI **`project-metadata-tree`** (см. `DesignerXmlCli`) строит обзор дерева метаданных по **корню проекта**: `src/cf`, при наличии — расширения `src/cfe/*/Configuration.xml`, плюс внешние отчёты и обработки в `src/erf` и `src/epf`. **ConfigDumpInfo.xml не используется.**

- `src/cf/Configuration.xml` — корневой `MetaDataObject` с `Configuration`; список объектов в `Configuration/ChildObjects`.
- `src/cf/Catalogs/<ИмяСправочника>.xml` — описание справочника: `MetaDataObject` с единственным `Catalog`.

## Имена в `ChildObjects`

Элементы списка справочников — **только имя** объекта (без префикса `Catalog.`), см. например:

```xml
<Catalog>_ДемоКассы</Catalog>
```

файл: `Catalogs/_ДемоКассы.xml`.

## Имя справочника

Идентификатор 1С: буква/подчёркивание в начале, далее буквы, цифры, подчёркивание; поддерживаются кириллические буквы (`\p{L}`).

## Добавление через md-sparrow

Создание справочника через `add-md-object --type CATALOG` (внутри `MdObjectAdd`) **всегда** формирует `Catalogs/<имя>.xml` из программной модели (**`NewCatalogXml`**, JAXB по XSD из `namespace-forest`); **не** читает и **не** копирует структуру с других файлов в `Catalogs/`. Для примеров полей справочников и регрессионных тестов используют submodule **`fixtures/ssl31`**; это **не** единственный эталон для атрибутов корня `Configuration.xml` (см. следующий раздел). Затем добавляется `<Catalog>имя</Catalog>` в `Configuration.xml`.

Строка в `Configuration.xml` вставляется **точечно** (без JAXB), с тем же отступом, что у строк `ChildObjects`. Порядок как в типовой выгрузке и в XSD `ConfigurationChildObjects`: **сначала** все типы метаданных до `Catalog` (`Language`, `Subsystem`, …, `CommonForm`), **затем** блок справочников; внутри блока — **по имени** (локаль `ru`, см. `ConfigurationChildObjectsOrder`). Если справочников ещё нет — вставка после последней строки любого типа «до Catalog»; если в файле уже идут только типы после `Catalog` (например `Document`) — новая строка вставляется **перед** первой такой. Дубликат имени проверяется по тексту `ChildObjects`.

## Пустая выгрузка, ssl31 и `Configuration/@formatVersion`

- **Эталон пустой конфигурации** — **[1c-platform-samples](https://github.com/yellow-hammer/1c-platform-samples) `src/cf`** (в т.ч. корневой `Configuration.xml`). Именно с ним сверять «как у конфигуратора» для пустой ИБ.
- **`fixtures/ssl31`** — типовая большая конфигурация для тестов чтения; **не** эталон пустой выгрузки.
- В **XSD** (`namespace-forest`) атрибут **`Configuration/@formatVersion` обязателен**. В **пустой выгрузке** (**1c-platform-samples**, `src/cf`) на `<Configuration>` этот атрибут **отсутствует** — так отдаёт платформа. Это **расхождение схемы и фактического XML конфигуратора**, а не ошибка выгрузки. При чтении/валидации в md-sparrow используется логика в **`XmlValidator`** (в т.ч. подстановка из `MetaDataObject/@version` там, где это предусмотрено).
- **`init-empty-cf`** / **`NewConfigurationXml`** при записи через JAXB **могут записывать** `formatVersion` — до выравнивания с эталоном **1c-platform-samples** вывод генератора **может отличаться** от пустой выгрузки по этому атрибуту; эталон при споре — **файлы в том репозитории**.

## Пустая выгрузка «как в конфигураторе» (без шаблона)

Подкоманда CLI **`init-empty-cf`** и метод `NewConfigurationXml.writeConfiguratorEmptyTree` **сначала полностью очищают** каталог `src/cf` (все объекты метаданных и файлы внутри), затем создают минимальный каталог:

- `Configuration.xml` — JAXB: `uuid` на `<Configuration>`; при marshaller часто ещё и `formatVersion` (в XSD обязателен, в эталоне **1c-platform-samples** на пустой выгрузке атрибута может не быть). Далее `InternalInfo`, в `ChildObjects` — язык «Русский», `DefaultLanguage` — `Language.Русский`, имя по умолчанию **«Конфигурация»**, пустой синоним/поставщик/версия и т.д.
- `Languages/Русский.xml` — JAXB `MetaDataObject` / `Language`.

Файл **`ConfigDumpInfo.xml`** при **`init-empty-cf`** / `writeConfiguratorEmptyTree` **не создаётся** (его формирует платформа при выгрузке; при необходимости — отдельно или вручную).

Один позиционный аргумент — **каталог `src/cf`**. Имя конфигурации: **`--name`** (по умолчанию «Конфигурация»). **`--synonym-ru`** по умолчанию не задаётся (пустой синоним). **`--vendor`** / **`--app-version`** по умолчанию пустые строки.

Для низкоуровневой записи только корня `Configuration.xml` без языка и без `ConfigDumpInfo.xml` остаётся `NewConfigurationXml.write(...)`.

Пример:

```text
init-empty-cf path/to/src/cf -v V2_21
init-empty-cf path/to/src/cf -v V2_20 --name МояБаза --synonym-ru "Моя база" --vendor "ООО Ромашка" --app-version 1.0.0
```

## Golden Writer (add-пути)

Для путей **создания** новых объектов (`init-empty-cf`, `add-md-object`, `external-artifact-add`) действует единый контракт «golden writer»:

- запись через JAXB/XSD (без runtime XML-шаблонов);
- детерминированные UUID для одинакового входа;
- единый post-process: стабильный корневой `MetaDataObject`/`xmlns`, `LF`, без `standalone="yes"`, self-closing пустые теги;
- идемпотентность: повторная запись того же состояния не меняет байты файла;
- обязательная пост-проверка: файл читается `DesignerXml.read`.

### Матрица add + fixture

| Команда | Тип | Источник fixture | Допустимые отличия |
| --- | --- | --- | --- |
| `add-md-object` | `CATALOG`, `ENUM`, `CONSTANT`, `DOCUMENT`, `REPORT`, `DATA_PROCESSOR`, `TASK`, `CHART_OF_ACCOUNTS`, `CHART_OF_CHARACTERISTIC_TYPES`, `CHART_OF_CALCULATION_TYPES`, `COMMON_MODULE`, `SUBSYSTEM`, `SESSION_PARAMETER`, `EXCHANGE_PLAN`, `COMMON_ATTRIBUTE`, `COMMON_PICTURE`, `DOCUMENT_NUMERATOR`, `EXTERNAL_DATA_SOURCE`, `ROLE` | `CATALOG`: snapshot `1c-platform-samples/snapshots/2.20/cf/empty-full-objects/Catalogs/Справочник1.xml`; остальные типы — JAXB/XSD генераторы (`New*Xml`) | `CATALOG`: только пустой `<ChildObjects/>` по умолчанию; остальные типы — детерминированные UUID и стабильный golden-format, без template-copy |

`external-artifact-add` пока **не имеет fixture snapshot** в workspace (`src/erf` / `src/epf` эталонов нет), поэтому для него зафиксированы инварианты: детерминизм, golden-format и читаемость JAXB без snapshot-сравнения.

Ограничение: мутации существующих объектов (`cf-md-object-set`, child-mutations, rename/delete/duplicate) в этот контракт не входят.
