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

Добавление узла, которого у вида владельца нет по схеме формата (`<Вид>ChildObjects` модели версии), отклоняется: «У вида Catalog нет подчинённых Dimension». Платформа такой узел при загрузке молча выбрасывает.

Структура объекта (секции, ТЧ, вложенные узлы) — `cf-md-object-structure-get`. Кроме состава она отдаёт синонимы, которыми платформа подписывает элементы формы: `standardAttributeSynonyms` - синонимы стандартных реквизитов (`Code` у справочника валют это «Цифровой код»), `commandSynonyms` - синонимы команд объекта. У табличных частей свои `standardAttributes` и `standardAttributeSynonyms`. Стандартные табличные части плана счетов и плана видов расчёта лежат в `standardTabularSections`: имя, подпись в `synonym` и подписи их стандартных реквизитов.

Синоним стандартного реквизита файл хранит только переопределённым, поэтому пустой заменяется подписью платформы: `LineNumber` это «N». Модель формата такие подписи не объявляет, словарь ведётся в `StandardAttributeLabels` и наполняется сверенным с конфигуратором.

`childSynonyms` - синонимы полей данных, которые в списках лежат одними именами: измерения, ресурсы, признаки учёта, колонки. По ним подписываются колонки динамического списка: его поля идут по именам основной таблицы, а её отдаёт `mainTable` у реквизита формы в `cf-form-content-get`.

### Допустимые значения перечислимых свойств

`cf-md-object-enums` отдаёт словарь `блок.свойство` → константы модели, например `{"chartOfCharacteristicTypes.codeSeries":["WHOLE_CHARACTERISTIC_KIND","WITHIN_SUBORDINATION"]}`. Набор снимается с модели запрошенной версии формата, поэтому вызывающей программе не нужна своя копия списка: при записи значение вне этого набора отклоняется с перечислением допустимых.

### Права ролей на объект

`cf-object-rights-get` (канал `read-json`) отдаёт права всех ролей на объект из `objectXml`: права его вида в порядке платформы (`rights`), права, которые каждое из них тянет за собой (`requires`), и у каждой роли действующие права с учётом «Устанавливать права для новых объектов», ограничения доступа и записанные права подчинённых. `editable` ложно у вида, прав на который в ролях нет.

`cf-object-rights-set` (канал `apply-mutation`) принимает `payloadJson` вида `{"edits":[{"role":"Кладовщик","right":"Read","value":true}]}`. Выданное право тянет нужные ему, снятое снимает зависящие; значение, равное умолчанию роли, из файла уходит, а право с ограничениями доступа остаётся с ними при любом значении. Роль на поддержке поставщика без возможности изменения не правится: в чтении у неё `readonlyReason`, запись отказывает с именем роли и не меняет файлы ни одной роли.

Обе операции работают с выгрузкой конфигуратора (`Roles/<Роль>/Ext/Rights.xml`) и с проектом 1С:EDT (`Roles/<Роль>/Rights.rights`, поддержка из `Configuration/Configuration.distr`).

## Создание объектов (`add-md-object`)

`add-md-object` создаёт объект верхнего уровня любого вида, кроме языка и устаревшего интерфейса: в выгрузке конфигуратора (путь к `Configuration.xml`, CLI и канал `apply-mutation`) и в проекте 1С:EDT (путь к `Configuration/Configuration.mdo`, канал `apply-mutation`). Вид задаёт `--type` (`type` в канале): имя константы из таблицы, регистр и разделители не важны, годится и имя элемента состава (`information-register`, `InformationRegister`). С `--auto-name` имя подбирается как у конфигуратора: префикс вида и первый свободный номер («РегистрСведений1»). Синоним задаётся только справочнику (`--synonym-ru`, `--synonym-empty`), у остальных видов он пустой, как в эталоне.

Новый объект - копия прототипа из эталона под новым именем и с новыми UUID: все файлы прототипа, а не только описание. В выгрузке конфигуратора это эталон платформы (см. [scaffold-golden.md](scaffold-golden.md#виды-объектов)), в проекте EDT - файлы, которые записала 1С:EDT при импорте той же выгрузки.

| `--type`                       | Элемент состава              | Каталог                       | Имя нового                     | С формата | Файлы кроме описания                                |
|--------------------------------|------------------------------|-------------------------------|--------------------------------|-----------|-----------------------------------------------------|
| `CATALOG`                      | `Catalog`                    | `Catalogs`                    | `Справочник1`                  | 2.10      |                                                     |
| `ENUM`                         | `Enum`                       | `Enums`                       | `Перечисление1`                | 2.10      |                                                     |
| `CONSTANT`                     | `Constant`                   | `Constants`                   | `Константа1`                   | 2.10      |                                                     |
| `DOCUMENT`                     | `Document`                   | `Documents`                   | `Документ1`                    | 2.10      |                                                     |
| `REPORT`                       | `Report`                     | `Reports`                     | `Отчет1`                       | 2.10      |                                                     |
| `DATA_PROCESSOR`               | `DataProcessor`              | `DataProcessors`              | `Обработка1`                   | 2.10      |                                                     |
| `TASK`                         | `Task`                       | `Tasks`                       | `Задача1`                      | 2.10      |                                                     |
| `CHART_OF_ACCOUNTS`            | `ChartOfAccounts`            | `ChartsOfAccounts`            | `ПланСчетов1`                  | 2.10      |                                                     |
| `CHART_OF_CHARACTERISTIC_TYPES`| `ChartOfCharacteristicTypes` | `ChartsOfCharacteristicTypes` | `ПланВидовХарактеристик1`      | 2.10      |                                                     |
| `CHART_OF_CALCULATION_TYPES`   | `ChartOfCalculationTypes`    | `ChartsOfCalculationTypes`    | `ПланВидовРасчета1`            | 2.10      |                                                     |
| `COMMON_MODULE`                | `CommonModule`               | `CommonModules`               | `ОбщийМодуль1`                 | 2.10      |                                                     |
| `SUBSYSTEM`                    | `Subsystem`                  | `Subsystems`                  | `Подсистема1`                  | 2.10      |                                                     |
| `SESSION_PARAMETER`            | `SessionParameter`           | `SessionParameters`           | `ПараметрСеанса1`              | 2.10      |                                                     |
| `EXCHANGE_PLAN`                | `ExchangePlan`               | `ExchangePlans`               | `ПланОбмена1`                  | 2.10      |                                                     |
| `COMMON_ATTRIBUTE`             | `CommonAttribute`            | `CommonAttributes`            | `ОбщийРеквизит1`               | 2.10      |                                                     |
| `COMMON_PICTURE`               | `CommonPicture`              | `CommonPictures`              | `ОбщаяКартинка1`               | 2.10      |                                                     |
| `DOCUMENT_NUMERATOR`           | `DocumentNumerator`          | `DocumentNumerators`          | `НумераторДокументов1`         | 2.10      |                                                     |
| `EXTERNAL_DATA_SOURCE`         | `ExternalDataSource`         | `ExternalDataSources`         | `ВнешнийИсточникДанных1`       | 2.10      |                                                     |
| `ROLE`                         | `Role`                       | `Roles`                       | `Роль1`                        | 2.10      | `Ext/Rights.xml`; EDT: `Rights.rights`              |
| `STYLE_ITEM`                   | `StyleItem`                  | `StyleItems`                  | `ЭлементСтиля1`                | 2.10      |                                                     |
| `STYLE`                        | `Style`                      | `Styles`                      | `Стиль1`                       | 2.10      |                                                     |
| `COMMON_TEMPLATE`              | `CommonTemplate`             | `CommonTemplates`             | `ОбщийМакет1`                  | 2.10      |                                                     |
| `FILTER_CRITERION`             | `FilterCriterion`            | `FilterCriteria`              | `КритерийОтбора1`              | 2.10      |                                                     |
| `XDTO_PACKAGE`                 | `XDTOPackage`                | `XDTOPackages`                | `ПакетXDTO1`                   | 2.10      |                                                     |
| `WEB_SERVICE`                  | `WebService`                 | `WebServices`                 | `WebСервис1`                   | 2.10      |                                                     |
| `HTTP_SERVICE`                 | `HTTPService`                | `HTTPServices`                | `HTTPСервис1`                  | 2.10      |                                                     |
| `WS_REFERENCE`                 | `WSReference`                | `WSReferences`                | `WSСсылка1`                    | 2.10      | `Ext/WSDefinition.xml`; EDT: `WsDefinitions.wsdl`   |
| `WEB_SOCKET_CLIENT`            | `WebSocketClient`            | `WebSocketClients`            | `WebSocketКлиент1`             | 2.20      |                                                     |
| `EVENT_SUBSCRIPTION`           | `EventSubscription`          | `EventSubscriptions`          | `ПодпискаНаСобытие1`           | 2.10      |                                                     |
| `SCHEDULED_JOB`                | `ScheduledJob`               | `ScheduledJobs`               | `РегламентноеЗадание1`         | 2.10      |                                                     |
| `SETTINGS_STORAGE`             | `SettingsStorage`            | `SettingsStorages`            | `ХранилищеНастроек1`           | 2.10      |                                                     |
| `FUNCTIONAL_OPTION`            | `FunctionalOption`           | `FunctionalOptions`           | `ФункциональнаяОпция1`         | 2.10      |                                                     |
| `FUNCTIONAL_OPTIONS_PARAMETER` | `FunctionalOptionsParameter` | `FunctionalOptionsParameters` | `ПараметрФункциональныхОпций1` | 2.10      |                                                     |
| `DEFINED_TYPE`                 | `DefinedType`                | `DefinedTypes`                | `ОпределяемыйТип1`             | 2.10      |                                                     |
| `BOT`                          | `Bot`                        | `Bots`                        | `Бот1`                         | 2.11      |                                                     |
| `PALETTE_COLOR`                | `PaletteColor`               | `PaletteColors`               | `ЦветПалитры1`                 | 2.21      |                                                     |
| `COMMON_COMMAND`               | `CommonCommand`              | `CommonCommands`              | `ОбщаяКоманда1`                | 2.10      |                                                     |
| `COMMAND_GROUP`                | `CommandGroup`               | `CommandGroups`               | `ГруппаКоманд1`                | 2.10      |                                                     |
| `COMMON_FORM`                  | `CommonForm`                 | `CommonForms`                 | `ОбщаяФорма1`                  | 2.10      | `Ext/Form.xml`; EDT: `Form.form`                    |
| `SEQUENCE`                     | `Sequence`                   | `Sequences`                   | `Последовательность1`          | 2.10      |                                                     |
| `DOCUMENT_JOURNAL`             | `DocumentJournal`            | `DocumentJournals`            | `ЖурналДокументов1`            | 2.10      |                                                     |
| `INFORMATION_REGISTER`         | `InformationRegister`        | `InformationRegisters`        | `РегистрСведений1`             | 2.10      |                                                     |
| `ACCUMULATION_REGISTER`        | `AccumulationRegister`       | `AccumulationRegisters`       | `РегистрНакопления1`           | 2.10      |                                                     |
| `ACCOUNTING_REGISTER`          | `AccountingRegister`         | `AccountingRegisters`         | `РегистрБухгалтерии1`          | 2.10      |                                                     |
| `CALCULATION_REGISTER`         | `CalculationRegister`        | `CalculationRegisters`        | `РегистрРасчета1`              | 2.10      |                                                     |
| `BUSINESS_PROCESS`             | `BusinessProcess`            | `BusinessProcesses`           | `БизнесПроцесс1`               | 2.10      |                                                     |
| `INTEGRATION_SERVICE`          | `IntegrationService`         | `IntegrationServices`         | `СервисИнтеграции1`            | 2.10      |                                                     |

Описание объекта лежит в выгрузке в `<Каталог>/<Имя>.xml`, файлы из последнего столбца - в `<Каталог>/<Имя>/…`; в проекте EDT все файлы лежат в `<Каталог>/<Имя>/`, описание - `<Имя>.mdo`.

**Формат.** Вид, которого в формате ещё нет, не создаётся, выгрузка и проект не меняются: «вид PaletteColor появился в формате 2.21 (платформа 8.5.1), в формате 2.20 его нет». Столбец «С формата» - по модели формата (`FormatProjection.hasObjectKind`), он же совпадает с тем, что выгружает платформа. Формат выгрузки - `-v` (`schemaVersion`), у проекта EDT - формат линейки его платформы (`Runtime-Version` в `DT-INF/PROJECT.PMF`, `SchemaVersion.ofPlatform`): в проекте 8.3.27 нет цвета палитры, в проекте 8.3.17 - бота и клиента WebSocket. Проект без манифеста принимает любой вид.

**Место в составе.** Ссылка в `Configuration.xml` встаёт в блок своего вида в порядке платформы (см. [cf-layout.md](cf-layout.md#добавление-объекта-add-md-object)), в `Configuration.mdo` - в порядке признаков класса `Configuration` схемы EDT.

**Общая форма.** Корень `Ext/Form.xml` объявляет пространство схемы компоновки (`xmlns:dcssch`) по режиму совместимости конфигурации, как платформа: с `Version8_3_19` объявляет, в более старых режимах и в `DontUse` - нет.

**Проверка конфигурации платформой.** Голые объекты 14 видов, как и новые объекты конфигуратора, обычную загрузку с проверкой не проходят - платформа требует заполнить свойства или состав. Загрузка без проверки (`ibcmd infobase config import files --no-check`) принимает все виды. Чем дополнить объект и есть ли для этого операция md-sparrow:

| Вид                          | Чего не хватает                          | Операция                                                    |
|------------------------------|------------------------------------------|-------------------------------------------------------------|
| `InformationRegister`        | измерения, ресурса или реквизита         | `cf-md-resource-add`, `cf-md-dimension-add`, `cf-md-attribute-add` |
| `AccumulationRegister`       | ресурса; документа-регистратора          | `cf-md-resource-add`; `registerRecords` документа в `cf-md-object-set` |
| `AccountingRegister`         | ресурса; документа-регистратора          | то же                                                       |
| `DocumentJournal`            | регистрируемых документов                | `registeredDocuments` в `cf-md-object-set`                  |
| `EventSubscription`          | источника, события, обработчика          | `source`, `event`, `handler` в `cf-md-object-set`           |
| `ScheduledJob`               | имени метода                             | `methodName` в `cf-md-object-set`                           |
| `CommonCommand`              | группы                                   | `group` в `cf-md-object-set`                                |
| `CalculationRegister`        | плана видов расчета; регистратора        | нет (план видов расчета)                                    |
| `BusinessProcess`            | задачи                                   | нет                                                         |
| `Sequence`                   | документов                               | нет                                                         |
| `FunctionalOption`           | хранения                                 | нет                                                         |
| `FunctionalOptionsParameter` | использования                            | нет                                                         |
| `WebService`                 | пространства имён                        | нет                                                         |
| `HTTPService`                | корневого URL                            | нет                                                         |

Проверено ibcmd 8.3.23, 8.3.24, 8.3.27 и 8.5.1: конфигурация из `init-empty-cf` со всеми видами формата, дополненная операциями из таблицы (у обработчиков подписки и задания - серверный общий модуль), без семи видов, для которых операции нет, проходит `ibcmd infobase config import` с проверкой.

EDT: те же виды и те же имена прототипов; файлы нового объекта совпадают с эталоном 1С:EDT после замены имени и идентификаторов, `classId` не меняется, подписи уходят в язык конфигурации.

## Поля JSON (`MdObjectPropertiesDto`)

Общие поля:

- `kind`: `"catalog"` \| `"constant"` \| `"enum"` \| `"document"` \| `"report"` \| `"dataProcessor"` \| `"task"` \| `"chartOfAccounts"` \| `"chartOfCharacteristicTypes"` \| `"chartOfCalculationTypes"` \| `"commonModule"` \| `"subsystem"` \| `"sessionParameter"` \| `"exchangePlan"` \| `"commonAttribute"` \| `"commonPicture"` \| `"documentNumerator"` \| `"eventSubscription"` \| `"scheduledJob"` \| `"commonCommand"` \| `"externalDataSource"` \| `"role"` \| `"documentJournal"` \| `"businessProcess"` \| `"informationRegister"` \| `"accumulationRegister"`; без своего блока свойств (имя, синоним, комментарий и состав): `"accountingRegister"` \| `"calculationRegister"` \| `"sequence"` \| `"filterCriterion"` \| `"settingsStorage"` \| `"functionalOption"` \| `"functionalOptionsParameter"` \| `"definedType"` \| `"commonForm"` \| `"commonTemplate"` \| `"commandGroup"` \| `"xdtoPackage"` \| `"webService"` \| `"httpService"` \| `"wsReference"` \| `"webSocketClient"` \| `"integrationService"` \| `"style"` \| `"styleItem"` \| `"bot"` \| `"paletteColor"` \| `"language"` \| `"interface"`
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
