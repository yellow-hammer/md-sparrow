# Scaffold новых объектов: golden для всех версий

Создание новых объектов метаданных (`add-md-object`, `init-empty-cf`, `external-artifact-add`)
для **любой** версии формата 2.10…2.21. Критерий корректности — **байт-в-байт** с выгрузкой
конфигуратора этой версии, кроме имени и детерминированно ремапнутых UUID.

## Почему не «из головы» и не из XSD
- Значения по умолчанию (`LevelCount=2`, `CodeLength=9`, `HierarchyType=…`, …) — артефакт
  **конфигуратора**, в XSD их нет (`grep -c 'default=' v8.1c.ru-8.3-MDClasses.xsd` → `0`).
- В эталоне есть и то, чего в XSD не бывает: UUID (`TypeId`/`ValueId`), ссылки на формы/реквизиты по имени.

Вывод: байт-в-байт возможен только из эталона нужной версии. XSD годится лишь для отладки структуры.

## Архитектура
1. **Источник правды** — per-version эталон **голого** объекта каждого вида в submodule
   `fixtures/samples-1c-platform`: `snapshots/<версия>/cf-bare-objects/<подкаталог>/<Прототип>.xml`
   (объекты конфигурации) и `snapshots/<версия>/external-files/empty/<Прототип>/<Прототип>.xml`
   (внешние отчёты/обработки). НЕ `empty-full-objects` (там формы и лишние ссылки), НЕ копирование «как есть».
2. **Генерация** — параметризация эталона без повторной сборки через JAXB (сохраняет форматирование):
   подстановка имени как целого токена + детерминированный ремап UUID.
   - `cf/GoldenScaffold` — фасад: `generateObject`, `generateEmptyConfiguration`, `generateExternalArtifact`,
     `generateRoleRights`, `generateRussianLanguage`.
   - `cf/GoldenObjectTemplate.parametrize(goldenXml, sourceName, targetName, uuidSeed)` — ядро
     (имя: граница `(?<![\p{L}\p{N}_])…(?![\p{L}\p{N}_])`; UUID: `DistinctUuidRewrite.remapDeterministic`).
   - `DistinctUuidRewrite` ремапит все UUID, **кроме `<xr:ClassId>`** — это фиксированный идентификатор
     класса метаданных платформы, а не объектно-зависимый UUID.
3. **Доступность версии = наличие эталона.** Никакого `== V2_20`: добавить версию = добавить её эталон,
   ноль правок Java (`GoldenScaffold.hasGolden` / `hasExternalGolden`).
4. **Пост-проверка** — результат читается `DesignerXml.read`/`unmarshal`. Идемпотентность и детерминизм
   покрыты тестами (`GoldenScaffoldTest`, `GoldenObjectTemplateTest`, `MdBoilerplateAddTest` — все 12 версий).

## Как получены эталоны
Вспомогательный re-runnable workflow `.github/workflows/diagnose-golden-cf.yml` (workflow_dispatch):
на каждой версии ставит платформу (как в namespace-forest: yard → `.run`/`.deb`), `ibcmd infobase create`,
`ibcmd config import <seed>` + `apply --force`, затем **дамп через `ibcmd infobase config export`**
(НЕ DESIGNER → без лицензии 1С) → `cf-bare-objects/<формат>/…` одним артефактом.

- **Семя** — один голый объект каждого вида (`fixtures/samples-1c-platform/seed/src/cf`),
  **сгенерировано самим md-sparrow** (`init-empty-cf` + `add-md-object` по 19 видам, формат 2.20).
- **Понижение версии.** Семя 2.20 импортируется в платформы 2.20+; для форматов < 2.20 семя понижается
  `cf/VersionTranscoder` (рефлексивный транскод 2.20→2.10, CLI `transcode`), затем экспортируется ibcmd.
- **Внешние объекты.** ibcmd не выгружает отдельные `.erf`/`.epf` (только DESIGNER/лицензия), поэтому
  эталон 2.20 транскодируется во все версии и проверяется round-trip (без сверки с платформой).

### Критерий корректности: ibcmd import, не XSD-валидация
Наш namespace-forest XSD **строже выгрузки конфигуратора**: требует `<ObjectBelonging>`, которого реальный
конфигуратор для родных объектов не пишет (выгрузка платформы сама не проходит XSD-валидацию). Поэтому
реальный гейт корректности объекта конфигурации — `ibcmd infobase config import`, а не XSD-валидация
(её вердикт «invalid» из-за `ObjectBelonging` — ложноотрицательный для родных объектов).

## Раскладка `src/cf` и порядок `ChildObjects`
См. [cf-layout.md](cf-layout.md).
