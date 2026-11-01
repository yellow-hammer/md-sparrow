# Упаковка fat JAR: уникальные ресурсы и сохранение notices

## Причина изменения

Официальный `md-sparrow-0.6.8-all.jar` (SHA-256
`041717fce94848302d00277ea81c885919bf1892c050c16ed9935a9d9892d182`)
содержит несколько ZIP entries с одинаковыми именами. IntelliJ Plugin Verifier
1.411 отклоняет такой JAR как malformed archive при прямом подключении Java API
в плагине: [InConfig Issue #7](https://github.com/smazovec/inconfig/issues/7),
[исходный CI failure](https://github.com/smazovec/inconfig/actions/runs/41302783334/job/124837812172).

Установка общего `EXCLUDE` теряет входы `mergeServiceFiles()` в Shadow 9.
Решение сохраняет слияние провайдеров и устраняет неоднозначность остальных
ресурсов при сборке исходного проекта. Официальный выпущенный файл не заменяется.

## Стратегия по классам конфликтов

| Ресурсы исходного fat JAR | До | После |
| --- | ---: | --- |
| `META-INF/LICENSE.md` | 6 | Каждый исходник в отдельном пути по Maven coordinate |
| `META-INF/NOTICE.md` | 6 | То же, без изменения байтов |
| `META-INF/LICENSE` | 2 | То же, без изменения байтов |
| `plugin.properties` | 3 | Одна рабочая properties-модель + все оригиналы по origin |
| `plugin.xml` | 2 | Оригиналы в отдельных origin-каталогах |
| `about.html` | 3 | То же |
| `about.ini`, `about.mappings`, `about.properties`, `modeling32.png` | По 2 | То же |
| `META-INF/services/*` | Нужны все входные descriptors | Штатное слияние Shadow, один descriptor с объединением providers |
| Любой иной повтор | Не контролировался | Ошибка сборки, без выбора первого файла |

Общий `duplicatesStrategy=FAIL`; `INCLUDE` включён только на входе services и
`plugin.properties`, которые обрабатываются трансформаторами. Дополнительно
`failOnDuplicateEntries` проверяет конечный ZIP. Политика Shadow для исходных
MANIFEST, module-info и устаревших подписей dependency JAR не менялась: это
одна classpath-поставка, а не объединение JPMS-модулей или подписей.

`plugin.properties` содержит сообщения EMF, поэтому его нельзя просто удалить
вместе с Eclipse descriptors. `PropertiesFileTransformer` сохраняет все ключи;
значения совпадающих описательных `pluginName` / `providerName` объединяются
через ` / `. Явная кодировка UTF-8 сохраняет Unicode при сериализации Shadow; Java 25 resource bundles читают этот формат. Для текущих зависимостей остальные рабочие ключи не конфликтуют.
Регрессия проверяет все исходные сообщения: появление конфликтующих значений
у рабочего ключа завершает `check` ошибкой. Оригиналы properties с attribution
также сохраняются побайтово.

Корневые `plugin.xml` относятся к двум разным Eclipse extensions. Набор
`about.*` и изображение — Eclipse bundle/about metadata. md-sparrow используется
как Java-библиотека и CLI, а не Eclipse/OSGi bundle. Эти файлы сохраняются под
origin вместо одного неоднозначного корня; Java classes и runtime services
не перемещаются. При наличии `about_files/` он сохраняется вместе с notices,
чтобы относительные ссылки из `about.html` не потеряли содержимое.

## Происхождение и лицензии

`prepareDependencyMetadata` создаёт в JAR:

```text
META-INF/dependencies/<group>/<artifact>/<version>/<original-path>
META-INF/dependencies/index.tsv
```

Индекс содержит Maven coordinate, первоначальное имя, SHA-256 исходных байтов и
новый путь. В нём нет абсолютных путей локального компьютера и времени сборки.
Добавлена также собственная `LICENCE` библиотеки. Ниже — карта источников
конфликтующих файлов для текущего runtimeClasspath:

| Dependency coordinate | Исходные metadata/notices |
| --- | --- |
| `com.fasterxml.woodstox:woodstox-core:7.1.0` | `META-INF/LICENSE` |
| `org.codehaus.woodstox:stax2-api:4.2.2` | `META-INF/LICENSE` |
| `jakarta.activation:jakarta.activation-api:2.1.3` | `META-INF/LICENSE.md`, `META-INF/NOTICE.md` |
| `jakarta.xml.bind:jakarta.xml.bind-api:4.0.2` | То же |
| `org.eclipse.angus:angus-activation:2.0.2` | То же |
| `org.glassfish.jaxb:jaxb-core:4.0.5` | То же |
| `org.glassfish.jaxb:jaxb-runtime:4.0.5` | То же |
| `org.glassfish.jaxb:txw2:4.0.5` | То же |
| `org.eclipse.emf:org.eclipse.emf.common:2.29.0` | `about.html`, `about.ini`, `about.mappings`, `about.properties`, `modeling32.png`, `plugin.properties` |
| `org.eclipse.emf:org.eclipse.emf.ecore:2.35.0` | То же + `plugin.xml` |
| `org.eclipse.emf:org.eclipse.emf.ecore.xmi:2.36.0` | `about.html`, `plugin.properties`, `plugin.xml` |
| `io.github.yellowhammer:md-sparrow:0.6.8` | Собственная `LICENCE` |

Это сохранение исходных уведомлений с проверяемым происхождением, а не
завершённая юридическая экспертиза состава всех зависимостей.

## Регрессионная проверка

```sh
./gradlew check shadowJar --no-daemon
```

`check` зависит от `verifyShadowJar`, поэтому существующий CI выполняет проверку
на Linux и Windows без изменения workflows или политики релизов. Проверка:

- читает центральный каталог конечного ZIP и отвергает любое повторяющееся имя;
- сверяет notices с каждым исходным dependency JAR побайтово и проверяет индекс;
- сравнивает все объединённые providers с исходными `META-INF/services`;
- проверяет сообщения EMF, классы API/JAXB/EMF, индексы и содержимое схем Designer
  XML / EDT, golden-ресурсы;
- загружает только конечный fat JAR в отдельном `URLClassLoader` с platform
  parent: JAXB/StAX providers обнаруживаются через ServiceLoader/TCCL,
  `ProjectMetadataTreeBuilder.build(Path)` читает искусственный проект;
- выполняет `java -jar <candidate> --help` на Java проверяющего процесса.

Вход smoke test находится в `src/test/resources/shadowjar-project`; он создан
для проверки упаковки по адресованной задаче, не скопирован из существующей
конфигурации. Это разрешённое данной задачей исключение к обычной политике
fixture-from-submodule. Он не претендует на полноту реальной конфигурации 1С.
Существующие schema/fixture submodules и их указатели не изменяются.

Чтобы проверить регрессию на исходном дефектном архиве:

```sh
./gradlew verifyShadowJar -PshadowJarToVerify=/path/to/md-sparrow-0.6.8-all.jar --no-daemon
```

Ожидается отказ именно по повторяющимся ZIP entries. Свойство меняет только
проверяемый вход, не скачивает и не перепаковывает официальный артефакт.

## Граница результата

Патч подготовлен от upstream `main`
`17c2afcf8c6b41bae0d212c13ee9697ddba9c2f6`. Исполнитель — Codex, предметный
владелец — Мастер / atlas; основание — [bounded task 6557628051](https://github.com/smazovec/inconfig/issues/7#issuecomment-6557628051).
Канон и локальный контекст atlas сверены на
`smazovec/mind-palace@463e256df7b27d18b799231ab60235f69463b0a9`.

Версия проекта остаётся `0.6.8`: локальный результат сборки — кандидат для
проверки, не новый официальный выпуск. Merge/release требует решения
upstream-maintainer. InConfig и PR #8 не меняются; после официального
исправленного релиза понадобится отдельная задача обновления фиксированной
версии/URL/checksum в прежнем PR #8. Срок чужого выпуска не обещается.
