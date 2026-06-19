# Журнал изменений

Все заметные изменения в этом проекте будут задокументированы в этом файле.

Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.0.0/),
и этот проект придерживается [Semantic Versioning](https://semver.org/lang/ru/).

## [0.3.2] - 2026-06-19


### Новые возможности

- **cli:** Добавили канал команд через UTF-8 JSON-файл


## [0.3.1] - 2026-06-18


### Новые возможности

- Добавлена поддержка автоматического выбора имени объекта


### Документация

- Контракт свойств объектов метаданных (cf-md-object)


### Обслуживание

- Единый релизный workflow с генерацией changelog

- Задачи VS Code в едином стиле


## [0.3.0] - 2026-06-08


### Новые возможности

- Поддержка всех форматов схем 2.10–2.21


### Прочее

- **xjc:** Автогенерация JAXB и bindings для форматов 2.10–2.21; переезд submodules


### Документация

- Документация и правила Cursor под golden-scaffold и in-memory валидацию

- Актуализация документации под golden-scaffold и in-memory валидацию


### Тестирование

- Покрытие всех форматов — scaffold, рефлексия, транскодер, валидация


### Обслуживание

- Вспомогательный workflow генерации эталонов конфигурации через ibcmd

- Ручной запуск release-workflow (workflow_dispatch с вводом версии)


## [0.2.0] - 2026-04-23


### Новые возможности

- **core:** Добавлена сборка графа метаданных и команда cf-md-graph

- **core:** Расширили чтение свойств для подписок, регзаданий и общих команд


### Документация

- Обновили README по актуальным CLI-командам и cf-md-graph

- Обновили README, добавив новые значки и ссылки на ресурсы


### Тестирование

- **core:** Добавили тесты парсера ссылок и сборки графа метаданных


### Обслуживание

- **cursor:** Уточнили правило по фикстурам для интеграционных тестов графа


## [0.1.1] - 2026-04-18


### Рефакторинг

- Replace AddCatalog with MdObjectAdd for catalog creation and update CLI commands


## [0.1.0] - 2026-04-18


### Новые возможности

- Инициализация Gradle и структуры проекта

- **core:** Add DesignerXml core, namespaces and validation utilities


### Прочее

- **ci:** Add release workflow and align developer tasks

- Make gradlew executable for Linux CI


### Документация

- Добавить README и лицензию LGPL-3.0

- Добавлен файл CONTRIBUTING.md с инструкциями по сборке и обновлению схем

- Document CF layout and refresh project onboarding notes


### Тестирование

- Add integration and golden tests for core CF workflows


### Обслуживание

- Добавлен .gitignore и submodule namespace-forest

- Добавлен файл конфигурации задач для VSCode

- Добавлен новый submodule для SSL 3.1

- Добавлен новый submodule для 1c-platform-samples

- **cursor:** Add md-sparrow project rules

- Update project version to 0.1.0

