# md-sparrow

[![OpenYellow](https://openyellow.openintegrations.dev/data/badges/1113279075.png)](https://openyellow.org/grid?filter=top&repo=1113279075)
[![telegram chat](resources/badges/telegram-chat.png)](https://t.me/wonder_yellow)
[![Ask Devin](resources/badges/deepwiki-badge.png)](https://deepwiki.com/yellow-hammer/md-sparrow)

Чтение и запись XML метаданных 1С (`MetaDataObject`) по XSD из [namespace-forest](https://github.com/yellow-hammer/namespace-forest). Поддержаны все версии формата выгрузки от 2.10 до 2.21.

Работает и как Java-библиотека, и как CLI: подходит и для встраивания в инструменты разработки, и для скриптов сборки.

## Что умеет

- Читать и писать свойства объектов метаданных, их реквизиты и табличные части, не трогая остальной файл: правка идёт точечно, форматирование и порядок элементов сохраняются.
- Создавать объекты, расширения и пустую выгрузку из эталонов, снятых с платформы, в формате нужной версии.
- Проверять выгрузку: валидация по XSD, целостность состава и ссылок, round-trip JAXB, перекодировка между версиями формата.
- Отдавать дерево и граф метаданных проекта в JSON для внешних инструментов.

## Начало работы

Нужен JDK 21 и submodule со схемами и эталонами:

```bash
git submodule update --init --recursive
./gradlew build
./gradlew shadowJar
```

Собранный `build/libs/md-sparrow-*-all.jar` запускается напрямую; список команд и их параметры выдаёт сама программа:

```bash
java -jar build/libs/md-sparrow-0.4.1-all.jar --help
```

## Документация

- [CONTRIBUTING.md](CONTRIBUTING.md) — сборка, тесты, схемы, выпуск JAR.
- [docs/cf-layout.md](docs/cf-layout.md) — раскладка `src/cf` и порядок `ChildObjects`.
- [docs/cf-md-object.md](docs/cf-md-object.md) — контракт свойств объектов: DTO и матрица поддержки по видам.
- [docs/form-content.md](docs/form-content.md) — содержимое управляемой формы в JSON.
- [docs/scaffold-golden.md](docs/scaffold-golden.md) — создание объектов по эталонам.
- [docs/validate-dump.md](docs/validate-dump.md) — проверка целостности выгрузки и виды находок.

## Лицензия

LGPL-3.0-or-later. Подробности см. в файле [LICENCE](LICENCE).

## Автор

Ivan Karlo (<i.karlo@outlook.com>)

При желании, отблагодарить автора можно по ссылке:

- [Boosty](https://boosty.to/1carlo/donate)
- [Чаевые](https://pay.cloudtips.ru/p/d752cb43)
