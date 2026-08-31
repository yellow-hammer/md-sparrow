# Свойства объектов метаданных (cf-md-object)

Контракт CLI-команд чтения и записи свойств объектов метаданных. Данные читаются и записываются только через JAXB: вызывающая программа получает и отдаёт DTO, XML на её стороне не правится.

## Команды CLI

| Команда                                      | Назначение           |
|----------------------------------------------|----------------------|
| `cf-md-object-get <путь.xml> -v V2_10…V2_21` | stdout: один JSON    |
| `cf-md-object-set <путь.xml> <json> -v …`    | запись из файла JSON |
| `cf-md-object-enums -v V2_10…V2_21`          | stdout: JSON допустимых значений перечислимых свойств |

### CRUD дочерних узлов объекта

| Команда                                               | Назначение                |
|-------------------------------------------------------|---------------------------|
| `cf-md-attribute-add/rename/delete/duplicate`         | Реквизиты объекта         |
| `cf-md-command-add/rename/delete`                     | Команды объекта           |
| `cf-md-tabular-section-add/rename/delete/duplicate`   | Табличные части объекта   |
| `cf-md-tabular-attribute-add/rename/delete/duplicate` | Реквизиты табличной части |

Структура объекта (секции, ТЧ, вложенные узлы) — `cf-md-object-structure-get`. Кроме состава она отдаёт синонимы, которыми платформа подписывает элементы формы: `standardAttributeSynonyms` - синонимы стандартных реквизитов (`Code` у справочника валют это «Цифровой код»), `commandSynonyms` - синонимы команд объекта. У табличных частей свои `standardAttributes` и `standardAttributeSynonyms`.

Синоним стандартного реквизита файл хранит только переопределённым, поэтому пустой заменяется подписью платформы: `LineNumber` это «N». Модель формата такие подписи не объявляет, словарь ведётся в `StandardAttributeLabels` и наполняется сверенным с конфигуратором.

`childSynonyms` - синонимы полей данных, которые в списках лежат одними именами: измерения, ресурсы, признаки учёта, колонки. По ним подписываются колонки динамического списка: его поля идут по именам основной таблицы, а её отдаёт `mainTable` у реквизита формы в `cf-form-content-get`.

### Допустимые значения перечислимых свойств

`cf-md-object-enums` отдаёт словарь `блок.свойство` → константы модели, например `{"chartOfCharacteristicTypes.codeSeries":["WHOLE_CHARACTERISTIC_KIND","WITHIN_SUBORDINATION"]}`. Набор снимается с модели запрошенной версии формата, поэтому вызывающей программе не нужна своя копия списка: при записи значение вне этого набора отклоняется с перечислением допустимых.

## Поля JSON (`MdObjectPropertiesDto`)

Общие поля:

- `kind`: `"catalog"` \| `"constant"` \| `"enum"` \| `"document"` \| `"report"` \| `"dataProcessor"` \| `"task"` \| `"chartOfAccounts"` \| `"chartOfCharacteristicTypes"` \| `"chartOfCalculationTypes"` \| `"commonModule"` \| `"subsystem"` \| `"sessionParameter"` \| `"exchangePlan"` \| `"commonAttribute"` \| `"commonPicture"` \| `"documentNumerator"` \| `"eventSubscription"` \| `"scheduledJob"` \| `"commonCommand"` \| `"externalDataSource"` \| `"role"` \| `"documentJournal"` \| `"businessProcess"`
- `internalName`: имя объекта (как в XML; при сохранении должно совпадать с именем файла без `.xml`)
- `synonymRu`, `comment`: строки; для `catalog` / `document` / `exchangePlan` синоним ru синхронизируется с представлениями так же, как в `cf-catalog-form-get/set`

## Матрица поддерживаемых типов

| kind (DTO)                   | containerLocal (XML)         | Поддержка полей                                                                     |
|------------------------------|------------------------------|-------------------------------------------------------------------------------------|
| `catalog`                    | `Catalog`                    | полная (`catalog`, `attributes`, `tabularSections`)                                  |
| `document`                   | `Document`                   | полная (`document`, `attributes`, `tabularSections`)                                 |
| `enum`                       | `Enum`                       | полная (`enumeration`, `enumValues`)                                                 |
| `constant`                   | `Constant`                   | полная (`constant`)                                                                  |
| `commonModule`               | `CommonModule`               | полная (`commonModule`)                                                              |
| `informationRegister`        | `InformationRegister`        | полная (`register`, `dimensions`, `resources`, `attributes`)                         |
| `accumulationRegister`       | `AccumulationRegister`       | полная (`register`, `dimensions`, `resources`, `attributes`)                         |
| `report`                     | `Report`                     | полная (`report`)                                                                    |
| `dataProcessor`              | `DataProcessor`              | полная (`report`, поля отчёта пусты)                                                 |
| `documentJournal`            | `DocumentJournal`            | полная (`documentJournal`, регистрируемые документы)                                 |
| `chartOfCharacteristicTypes` | `ChartOfCharacteristicTypes` | полная (`chartOfCharacteristicTypes`, `attributes`, `tabularSections`)               |
| `exchangePlan`               | `ExchangePlan`               | полная (`exchangePlan`, `attributes`, `tabularSections`; состав - `cf-md-exchange-plan-content-set`) |
| `task`                       | `Task`                       | полная (`task`, `attributes`, `tabularSections`)                                     |
| `businessProcess`            | `BusinessProcess`            | полная (`businessProcess`, `attributes`, `tabularSections`)                          |
| `chartOfAccounts`            | `ChartOfAccounts`            | полная (`chartOfAccounts`, `attributes`, `tabularSections`)                          |
| `chartOfCalculationTypes`    | `ChartOfCalculationTypes`    | полная (`chartOfCalculationTypes`, `attributes`, `tabularSections`)                  |
| `subsystem`                  | `Subsystem`                  | расширенная (`nestedSubsystems`, `contentRefs`)                                      |

| `sessionParameter`            | `SessionParameter`            | полная (`sessionParameter`: тип значения) |
| `commonAttribute` | `CommonAttribute` | полная (`commonAttribute`: разделение данных, поле ввода) |
| `commonPicture` | `CommonPicture` | полная (`commonPicture`: доступность картинки) |
| `documentNumerator`           | `DocumentNumerator`           | полная (`documentNumerator`: нумерация) |
| `eventSubscription`           | `EventSubscription`           | полная (`eventSubscription`: источник, событие, обработчик) |
| `scheduledJob`                | `ScheduledJob`                | полная (`scheduledJob`: метод, ключ, расписание, перезапуски) |
| `commonCommand`               | `CommonCommand`               | полная (`commonCommand`: группа, параметр, представление) |
| `externalDataSource` | `ExternalDataSource` | полная (`externalDataSource`: режим блокировки) |
| `role` | `Role` | полная (`role`: общие поля; состав прав - отдельный файл) |

Базовая поддержка — `internalName`, `synonymRu`, `comment`; полная — плюс типизированный блок свойств вида,
который читается и пишется целиком, гранулярно по изменённым элементам.

Для `catalog`, `document`, `exchangePlan`:

- `attributes[]`, `tabularSections[]`: элементы `{ "name", "synonymRu", "comment", "type" }` плюс свойства палитры: `toolTipRu`, `fillChecking`, `indexing`, `fullTextSearch`, `dataHistory`, `use`, `quickChoice`, `createOnInput`, `choiceHistoryOnInput`, `choiceForm`, `choiceParameters`, `choiceParameterLinks`. Набор свойств зависит от вида узла и версии формата: чего в схеме нет, приходит пустым и при записи не трогается. Допустимые значения перечислений отдаёт `cf-md-object-enums` под ключами вида `attribute.indexing`. Имя **не меняется** через `cf-md-object-set`; при сохранении число и порядок элементов должны совпадать с XML.

Для `subsystem`:

- `nestedSubsystems[]`: строки — вложенные подсистемы в `ChildObjects`
- `contentRefs[]`: состав подсистемы из `Properties/Content`, читается и пишется через `cf-md-object-set`

## Эталоны для проверки

Round-trip и регрессии — на выгрузках вроде submodule **fixtures/ssl31**; пустая конфигурация — [samples-1c-platform](https://github.com/yellow-hammer/samples-1c-platform) (см. правила эталона пустой выгрузки).

## Ограничения текущего этапа

- Для всех перечисленных `kind` поддержаны базовые поля `internalName/synonymRu/comment` с гранулярной записью.
- Типизированный блок свойств есть у видов из строк «полная»; остальным доступны только базовые поля.
- Для `subsystem` дополнительно поддержаны `nestedSubsystems` и `contentRefs`. Тем же полем `contentRefs` приходят состав функциональной опции и её параметра, общего реквизита, последовательности и критерия отбора: у критерия ссылки только снимаются, дерево кандидатов не строится.
- Параметры выбора (`choiceParameters`) читаются текстом и не пишутся: значение платформа хранит типизированным, строкой его не восстановить. Связи параметров выбора (`choiceParameterLinks`: `name`, `dataPath`, `mode`) читаются и пишутся целиком, число и порядок связей при записи должны совпадать с XML.
- Состав плана обмена лежит отдельным файлом `<План>/Ext/Content.xml` и правится операцией `cf-md-exchange-plan-content-set`.
