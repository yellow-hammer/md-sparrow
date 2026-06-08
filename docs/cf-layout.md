# Раскладка каталога конфигурации (`src/cf`)

Структура `src/cf` согласована с выгрузкой конфигуратора. **Типовая большая** выгрузка для тестов —
submodule **`fixtures/ssl31`** (не путать с пустой ИБ). **Эталоны** голых объектов и пустой конфигурации —
submodule **`fixtures/samples-1c-platform`** (см. [scaffold-golden.md](scaffold-golden.md)).

Команда CLI **`project-metadata-tree`** строит обзор дерева метаданных по корню проекта: `src/cf`,
при наличии — расширения `src/cfe/*/Configuration.xml`, плюс внешние отчёты/обработки в `src/erf` и `src/epf`.
**ConfigDumpInfo.xml не используется.**

- `src/cf/Configuration.xml` — корневой `MetaDataObject` с `Configuration`; список объектов в `Configuration/ChildObjects`.
- `src/cf/Catalogs/<ИмяСправочника>.xml` — `MetaDataObject` с единственным `Catalog`.

## Имена в `ChildObjects`

Элементы списка — **только имя** объекта (без префикса `Catalog.`):

```xml
<Catalog>_ДемоКассы</Catalog>
```

файл: `Catalogs/_ДемоКассы.xml`.

## Имя справочника

Идентификатор 1С: буква/подчёркивание в начале, далее буквы, цифры, подчёркивание; кириллица поддерживается (`\p{L}`).

## Добавление объекта (`add-md-object`)

Создание объекта (`MdObjectAdd`) формирует `<Подкаталог>/<имя>.xml` параметризацией golden-эталона нужной
версии (см. [scaffold-golden.md](scaffold-golden.md)) — **не** читает и **не** копирует структуру других файлов
в каталоге. Затем имя добавляется в `Configuration.xml`.

Строка в `Configuration.xml` вставляется **точечно** (без JAXB), с тем же отступом, что у строк `ChildObjects`.
Порядок как в выгрузке и в XSD `ConfigurationChildObjects`: **сначала** все типы до `Catalog`
(`Language`, `Subsystem`, …, `CommonForm`), **затем** блок справочников; внутри блока — **по имени**
(локаль `ru`, см. `ConfigurationChildObjectsOrder`). Дубликат имени проверяется по тексту `ChildObjects`.

## Пустая выгрузка (`init-empty-cf`)

Подкоманда **`init-empty-cf`** (`EmptyCfScaffold.writeEmptyTree`) **сначала полностью очищает** `src/cf`,
затем пишет минимальный каталог из golden-эталона версии:

- `Configuration.xml` — эталон с `ChildObjects`, обрезанным до языка «Русский»; `DefaultLanguage` —
  `Language.Русский`, имя по умолчанию **«Конфигурация»**, пустые синоним/поставщик/версия; UUID ремапятся
  детерминированно.
- `Languages/Русский.xml` — из эталона.

`ConfigDumpInfo.xml` не создаётся (его формирует платформа при выгрузке).

Один позиционный аргумент — каталог `src/cf`. Имя конфигурации: **`--name`** (по умолчанию «Конфигурация»).
**`--synonym-ru`** по умолчанию пустой. **`--vendor`** / **`--app-version`** по умолчанию пустые строки.

```text
init-empty-cf path/to/src/cf -v V2_21
init-empty-cf path/to/src/cf -v V2_20 --name МояБаза --synonym-ru "Моя база" --vendor "ООО Ромашка" --app-version 1.0.0
```

> **`Configuration/@formatVersion`:** в XSD атрибут обязателен, но в реальной выгрузке платформы на
> `<Configuration>` его нет (версия только в `MetaDataObject/@version`). Golden-эталоны повторяют поведение
> платформы; при чтении/валидации расхождение сглаживает `XmlValidator` (подстановка из `MetaDataObject/@version`).

## `fixtures/ssl31` vs `fixtures/samples-1c-platform`

- **`fixtures/ssl31`** — типовая большая конфигурация для тестов **чтения**; не эталон пустой выгрузки.
- **`fixtures/samples-1c-platform`** — эталоны голых объектов и пустой конфигурации для **scaffold**
  (cf-bare-objects / external-files / seed; см. [scaffold-golden.md](scaffold-golden.md)).
