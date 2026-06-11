# Свойства объектов метаданных (cf-md-object)

Контракт CLI-команд чтения и записи свойств объектов метаданных. Потребитель — расширение [vscode-1c-platform-tools](https://github.com/yellow-hammer/vscode-1c-platform-tools) (форма «Свойства объекта»); данные читаются и записываются только через JAXB, без правки XML на стороне клиента.

## Команды CLI

| Команда                                      | Назначение           |
|----------------------------------------------|----------------------|
| `cf-md-object-get <путь.xml> -v V2_10…V2_21` | stdout: один JSON    |
| `cf-md-object-set <путь.xml> <json> -v …`    | запись из файла JSON |

### CRUD дочерних узлов объекта

| Команда                                               | Назначение                |
|-------------------------------------------------------|---------------------------|
| `cf-md-attribute-add/rename/delete/duplicate`         | Реквизиты объекта         |
| `cf-md-tabular-section-add/rename/delete/duplicate`   | Табличные части объекта   |
| `cf-md-tabular-attribute-add/rename/delete/duplicate` | Реквизиты табличной части |

Структура объекта (секции, ТЧ, вложенные узлы) — `cf-md-object-structure-get`.

## Поля JSON (`MdObjectPropertiesDto`)

Общие поля:

- `kind`: `"catalog"` \| `"constant"` \| `"enum"` \| `"document"` \| `"report"` \| `"dataProcessor"` \| `"task"` \| `"chartOfAccounts"` \| `"chartOfCharacteristicTypes"` \| `"chartOfCalculationTypes"` \| `"commonModule"` \| `"subsystem"` \| `"sessionParameter"` \| `"exchangePlan"` \| `"commonAttribute"` \| `"commonPicture"` \| `"documentNumerator"` \| `"externalDataSource"` \| `"role"`
- `internalName`: имя объекта (как в XML; при сохранении должно совпадать с именем файла без `.xml`)
- `synonymRu`, `comment`: строки; для `catalog` / `document` / `exchangePlan` синоним ru синхронизируется с представлениями так же, как в `cf-catalog-form-get/set`

## Матрица поддерживаемых типов

| kind (DTO)                   | containerLocal (XML)         | Поддержка полей                                                                  |
|------------------------------|------------------------------|----------------------------------------------------------------------------------|
| `catalog`                    | `Catalog`                    | расширенная (`catalog`, `attributes`, `tabularSections`, `synonymRu`, `comment`) |
| `document`                   | `Document`                   | расширенная (`attributes`, `tabularSections`, `synonymRu`, `comment`)            |
| `subsystem`                  | `Subsystem`                  | расширенная (`nestedSubsystems`, `contentRefs` чтение, `synonymRu`, `comment`)   |
| `exchangePlan`               | `ExchangePlan`               | расширенная (`attributes`, `tabularSections`, `synonymRu`, `comment`)            |
| `constant`                   | `Constant`                   | базовая (`synonymRu`, `comment`)                                                 |
| `enum`                       | `Enum`                       | базовая (`synonymRu`, `comment`)                                                 |
| `report`                     | `Report`                     | базовая (`synonymRu`, `comment`)                                                 |
| `dataProcessor`              | `DataProcessor`              | базовая (`synonymRu`, `comment`)                                                 |
| `task`                       | `Task`                       | базовая (`synonymRu`, `comment`)                                                 |
| `chartOfAccounts`            | `ChartOfAccounts`            | базовая (`synonymRu`, `comment`)                                                 |
| `chartOfCharacteristicTypes` | `ChartOfCharacteristicTypes` | базовая (`synonymRu`, `comment`)                                                 |
| `chartOfCalculationTypes`    | `ChartOfCalculationTypes`    | базовая (`synonymRu`, `comment`)                                                 |
| `commonModule`               | `CommonModule`               | базовая (`synonymRu`, `comment`)                                                 |
| `sessionParameter`           | `SessionParameter`           | базовая (`synonymRu`, `comment`)                                                 |
| `commonAttribute`            | `CommonAttribute`            | базовая (`synonymRu`, `comment`)                                                 |
| `commonPicture`              | `CommonPicture`              | базовая (`synonymRu`, `comment`)                                                 |
| `documentNumerator`          | `DocumentNumerator`          | базовая (`synonymRu`, `comment`)                                                 |
| `externalDataSource`         | `ExternalDataSource`         | базовая (`synonymRu`, `comment`)                                                 |
| `role`                       | `Role`                       | базовая (`synonymRu`, `comment`)                                                 |

Для `catalog`, `document`, `exchangePlan`:

- `attributes[]`, `tabularSections[]`: элементы `{ "name", "synonymRu", "comment" }`. Имя **не меняется** через `cf-md-object-set`; при сохранении число и порядок элементов должны совпадать с XML.

Для `subsystem`:

- `nestedSubsystems[]`: строки — вложенные подсистемы в `ChildObjects`
- `contentRefs[]`: только **чтение** (состав подсистемы из `Properties/Content`); при `cf-md-object-set` не изменяется

## Эталоны для проверки

Round-trip и регрессии — на выгрузках вроде submodule **fixtures/ssl31**; пустая конфигурация — [samples-1c-platform](https://github.com/yellow-hammer/samples-1c-platform) (см. правила эталона пустой выгрузки).

## Ограничения текущего этапа

- Для всех перечисленных `kind` поддержаны базовые поля `internalName/synonymRu/comment` с гранулярной записью.
- Расширенные поля `catalog` и список `attributes/tabularSections` остаются полными только для типов, где это уже реализовано (`catalog`, `document`, `exchangePlan`).
- Для `subsystem` дополнительно поддержаны `nestedSubsystems` и чтение `contentRefs`.
