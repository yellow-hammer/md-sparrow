/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * md-sparrow is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * md-sparrow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with md-sparrow.
 */
package io.github.yellowhammer.designerxml.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.cf.SupportRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Протокол {@code serve} на сеансе в том же процессе. */
class ServeSessionTest {

  private static final Path LANGUAGE_FIXTURE =
    Path.of("src", "test", "resources", "cf-language-de").toAbsolutePath();

  @TempDir
  Path tempDir;

  private Client client;

  /** Первая строка сеанса. */
  private JsonObject ready;

  @BeforeEach
  void startSession() throws Exception {
    client = new Client();
    ready = client.next();
  }

  @AfterEach
  void stopSession() throws Exception {
    client.finish();
  }

  @Test
  void готовностьСообщаетВерсиюИПротокол() throws Exception {
    assertThat(ready.keySet()).containsExactly("ready", "version", "protocol");
    assertThat(ready.get("ready").getAsBoolean()).isTrue();
    assertThat(ready.get("version").getAsString()).isNotBlank();
    assertThat(ready.get("protocol").getAsInt()).isEqualTo(ServeSession.PROTOCOL);
    assertThat(client.finish()).isZero();
  }

  @Test
  void ответСовпадаетСРазовымЗапуском() throws Exception {
    String catalog = Ssl31SubmodulePaths.anyCatalogObjectXml().toString();
    String missing = tempDir.resolve("Нет.xml").toString();

    JsonObject read = client.request(1, "cf-md-object-get", catalog, "-v", "V2_20");
    JsonObject failed = client.request(2, "cf-md-object-get", missing, "-v", "V2_20");
    JsonObject usage = client.request(3);

    assertSameAsOneShot(read, "cf-md-object-get", catalog, "-v", "V2_20");
    assertThat(read.get("exitCode").getAsInt()).isZero();
    assertSameAsOneShot(failed, "cf-md-object-get", missing, "-v", "V2_20");
    assertThat(failed.get("exitCode").getAsInt()).isEqualTo(2);
    assertSameAsOneShot(usage);
  }

  @Test
  void ответыИдутВПорядкеЗапросов() throws Exception {
    String catalog = Ssl31SubmodulePaths.anyCatalogObjectXml().toString();

    client.send(requestLine(1, null, "cf-md-object-get", catalog, "-v", "V2_20"));
    client.send(requestLine(2, null, "--version"));

    JsonObject first = client.next();
    JsonObject second = client.next();
    assertThat(first.get("id").getAsLong()).isEqualTo(1);
    assertThat(first.get("stdout").getAsString()).contains("\"internalName\"");
    assertThat(second.get("id").getAsLong()).isEqualTo(2);
    assertThat(second.get("stdout").getAsString()).startsWith("md-sparrow ");
  }

  @Test
  void кириллицаИПробелыВАргументахИПараметрах() throws Exception {
    Path project = copyLanguageFixture("проект с пробелами");
    Path catalog = project.resolve("Catalogs").resolve("Партнеры.xml");
    Path params = project.resolve("параметры чтения.json");
    Files.writeString(params, "{\"op\":\"cf-md-object-get\",\"objectXml\":" + json(catalog.toString())
      + ",\"schemaVersion\":\"V2_20\"}", StandardCharsets.UTF_8);

    JsonObject byArgs = client.request(1, "cf-md-object-get", catalog.toString(), "-v", "V2_20");
    JsonObject byParams = client.request(2, "read-json", "--params", params.toString());

    assertThat(byArgs.get("exitCode").getAsInt()).isZero();
    assertThat(byArgs.get("stdout").getAsString()).contains("\"Партнеры\"", "Geschäftspartner");
    assertThat(byParams.get("exitCode").getAsInt()).isZero();
    assertThat(byParams.get("stdout").getAsString()).contains("\"Партнеры\"", "Geschäftspartner");
  }

  @Test
  void относительныеПутиСчитаютсяОтКаталогаЗапроса() throws Exception {
    Path project = copyLanguageFixture("cf");
    Files.writeString(project.resolve("params.json"),
      "{\"op\":\"cf-md-object-get\",\"objectXml\":\"Catalogs/Партнеры.xml\",\"schemaVersion\":\"V2_20\"}",
      StandardCharsets.UTF_8);
    String relative = Path.of("Catalogs", "Партнеры.xml").toString();

    client.send(requestLine(1, project, "cf-md-object-get", relative, "-v", "V2_20"));
    JsonObject inProject = client.next();
    client.send(requestLine(2, project, "read-json", "--params", "params.json"));
    JsonObject paramsInProject = client.next();
    JsonObject withoutCwd = client.request(3, "cf-md-object-get", relative, "-v", "V2_20");
    JsonObject absolute = client.request(
      4, "cf-md-object-get", project.resolve(relative).toString(), "-v", "V2_20");

    assertThat(inProject.get("exitCode").getAsInt()).isZero();
    assertThat(inProject.get("stdout").getAsString()).isEqualTo(absolute.get("stdout").getAsString());
    assertThat(paramsInProject.get("exitCode").getAsInt()).isZero();
    assertThat(paramsInProject.get("stdout").getAsString()).contains("\"Партнеры\"");
    assertThat(withoutCwd.get("exitCode").getAsInt()).isEqualTo(2);
  }

  @Test
  void пакетОперацийСчитаетПутиОтКаталогаЗапроса() throws Exception {
    Path project = copyLanguageFixture("cf");
    Files.writeString(project.resolve("batch.json"), "{\"op\":\"batch\",\"operations\":["
      + "{\"op\":\"add-md-object\",\"configurationXml\":\"Configuration.xml\",\"type\":\"CATALOG\","
      + "\"name\":\"Waren\",\"schemaVersion\":\"V2_20\"},"
      + "{\"op\":\"cf-form-add\",\"objectXml\":\"Catalogs/Waren.xml\",\"name\":\"Форма\","
      + "\"schemaVersion\":\"V2_20\"}]}", StandardCharsets.UTF_8);

    client.send(requestLine(1, project, "apply-mutation", "--params", "batch.json"));
    JsonObject response = client.next();

    assertThat(response.get("exitCode").getAsInt()).as(response.get("stderr").getAsString()).isZero();
    assertThat(response.get("stdout").getAsString().strip()).isEqualTo("[\"OK\",\"OK\"]");
    assertThat(project.resolve("Catalogs/Waren/Forms/Форма/Ext/Form.xml")).isRegularFile();
  }

  @Test
  void отменаПрерываетВыполняемыйЗапрос() throws Exception {
    String project = Ssl31SubmodulePaths.projectRoot().toString();

    client.send(requestLine(1, null, "cf-md-graph", project));
    Thread.sleep(300);
    long cancelledAt = System.nanoTime();
    client.send("{\"id\":1,\"cancel\":true}");
    JsonObject cancelled = client.next();
    long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cancelledAt);

    assertThat(cancelled.get("id").getAsLong()).isEqualTo(1);
    assertThat(cancelled.get("exitCode").getAsInt()).isEqualTo(ServeSession.EXIT_CANCELLED);
    assertThat(cancelled.get("stdout").getAsString()).isEmpty();
    assertThat(cancelled.get("stderr").getAsString()).contains("отменён");
    assertThat(waited).isLessThan(2_000);
    // После отмены сеанс работает дальше
    JsonObject next = client.request(2, "--version");
    assertThat(next.get("exitCode").getAsInt()).isZero();
  }

  @Test
  void отменаЖдущегоЗапросаОтвечаетСразу() throws Exception {
    String project = Ssl31SubmodulePaths.projectRoot().toString();

    client.send(requestLine(1, null, "cf-md-graph", project));
    client.send(requestLine(2, null, "--version"));
    client.send("{\"id\":2,\"cancel\":true}");
    JsonObject queued = client.next();
    client.send("{\"id\":1,\"cancel\":true}");
    JsonObject running = client.next();

    assertThat(queued.get("id").getAsLong()).isEqualTo(2);
    assertThat(queued.get("exitCode").getAsInt()).isEqualTo(ServeSession.EXIT_CANCELLED);
    assertThat(running.get("id").getAsLong()).isEqualTo(1);
    assertThat(running.get("exitCode").getAsInt()).isEqualTo(ServeSession.EXIT_CANCELLED);
  }

  @Test
  void отменаНеизвестногоИЗавершённогоЗапросаНеОтвечает() throws Exception {
    JsonObject done = client.request(1, "--version");
    client.send("{\"id\":1,\"cancel\":true}");
    client.send("{\"id\":99,\"cancel\":true}");
    JsonObject next = client.request(2, "--version");

    assertThat(done.get("exitCode").getAsInt()).isZero();
    assertThat(next.get("id").getAsLong()).isEqualTo(2);
    assertThat(next.get("exitCode").getAsInt()).isZero();
  }

  @Test
  void невернаяСтрокаПолучаетОтказИСеансРаботаетДальше() throws Exception {
    client.send("не json");
    JsonObject notJson = client.next();
    client.send("[1,2]");
    JsonObject notObject = client.next();
    client.send("{\"id\":3,\"args\":[\"--version\"]} лишнее");
    JsonObject trailing = client.next();
    client.send("{\"args\":[\"--version\"]}");
    JsonObject noId = client.next();
    client.send("{\"id\":\"пять\",\"args\":[\"--version\"]}");
    JsonObject textId = client.next();
    client.send("{\"id\":6,\"args\":\"--version\"}");
    JsonObject argsNotArray = client.next();
    client.send("{\"id\":7,\"args\":[1]}");
    JsonObject argsNotStrings = client.next();
    client.send("{\"id\":8,\"args\":[\"--version\"],\"cwd\":\"relative\"}");
    JsonObject relativeCwd = client.next();
    client.send("{\"id\":9,\"args\":[\"--version\"],\"cwd\":" + json(tempDir.resolve("нет").toString()) + "}");
    JsonObject missingCwd = client.next();

    assertInvalid(notJson, null);
    assertInvalid(notObject, null);
    // Строку с хвостом разобрать нельзя, и id из неё не берётся
    assertInvalid(trailing, null);
    assertInvalid(noId, null);
    assertThat(textId.get("id").getAsString()).isEqualTo("пять");
    assertThat(textId.get("exitCode").getAsInt()).isEqualTo(2);
    assertInvalid(argsNotArray, 6L);
    assertInvalid(argsNotStrings, 7L);
    assertInvalid(relativeCwd, 8L);
    assertInvalid(missingCwd, 9L);

    JsonObject valid = client.request(10, "--version");
    assertThat(valid.get("exitCode").getAsInt()).isZero();
  }

  @Test
  void сбойКомандыНеРоняетСеанс() throws Exception {
    String missing = tempDir.resolve("нет.xml").toString();
    String output = tempDir.resolve("out.xml").toString();

    JsonObject crashed = client.request(1, "round-trip", missing, output, "-v", "V2_20");
    JsonObject nested = client.request(2, "serve");
    JsonObject unknown = client.request(3, "нет-такой-команды");
    JsonObject after = client.request(4, "--version");

    assertSameAsOneShot(crashed, "round-trip", missing, output, "-v", "V2_20");
    assertThat(crashed.get("exitCode").getAsInt()).isEqualTo(1);
    assertThat(nested.get("exitCode").getAsInt()).isEqualTo(2);
    assertThat(nested.get("stdout").getAsString()).isEmpty();
    assertThat(nested.get("stderr").getAsString()).contains("serve");
    assertSameAsOneShot(unknown, "нет-такой-команды");
    assertThat(after.get("exitCode").getAsInt()).isZero();
  }

  @Test
  void концВводаЗавершаетСеансПослеПринятыхЗапросов() throws Exception {
    client.send(requestLine(1, null, "--version"));

    int exit = client.finish();

    assertThat(exit).isZero();
    assertThat(client.next().get("id").getAsLong()).isEqualTo(1);
  }

  @Test
  void shutdownЗавершаетСеансИЗакрываетПриём() throws Exception {
    client.send(requestLine(1, null, "--version"));
    client.send("{\"shutdown\":true}");
    client.send(requestLine(2, null, "--version"));

    assertThat(client.awaitExit()).isZero();
    // Отказ пишет поток чтения, поэтому он может прийти раньше ответа на принятый запрос
    java.util.Map<Long, Integer> exitById = new java.util.HashMap<>();
    for (int i = 0; i < 2; i++) {
      JsonObject response = client.next();
      exitById.put(response.get("id").getAsLong(), response.get("exitCode").getAsInt());
    }
    assertThat(exitById).containsEntry(1L, 0).containsEntry(2L, 2);
  }

  @Test
  void повторноеЧтениеВидитПравкуНаДиске() throws Exception {
    Path project = copyLanguageFixture("cf");
    String catalog = project.resolve("Catalogs").resolve("Партнеры.xml").toString();
    Path language = project.resolve("Languages").resolve("Немецкий.xml");

    JsonObject before = client.request(1, "cf-md-object-get", catalog, "-v", "V2_20");
    replaceInFile(Path.of(catalog), "Geschäftspartner", "Lieferanten");
    JsonObject afterObject = client.request(2, "cf-md-object-get", catalog, "-v", "V2_20");
    // Описание конфигурации не меняется: язык берётся из файла самого языка
    replaceInFile(language, "<LanguageCode>de</LanguageCode>", "<LanguageCode>at</LanguageCode>");
    JsonObject afterLanguage = client.request(3, "cf-md-object-get", catalog, "-v", "V2_20");

    assertThat(dto(before).get("synonym").getAsString()).isEqualTo("Geschäftspartner");
    assertThat(dto(before).get("languageCode").getAsString()).isEqualTo("de");
    assertThat(dto(afterObject).get("synonym").getAsString()).isEqualTo("Lieferanten");
    assertThat(dto(afterLanguage).get("languageCode").getAsString()).isEqualTo("at");
  }

  @Test
  void запросНачинаетСПравилПоддержкиПоУмолчанию() throws Exception {
    SupportRules.setEnforced(false);

    JsonObject response = client.request(1, "--version");

    assertThat(response.get("exitCode").getAsInt()).isZero();
    assertThat(SupportRules.isEnforced()).isTrue();
  }

  private static JsonObject dto(JsonObject response) {
    assertThat(response.get("exitCode").getAsInt()).isZero();
    return JsonParser.parseString(response.get("stdout").getAsString()).getAsJsonObject();
  }

  private static void assertInvalid(JsonObject response, Long id) {
    if (id == null) {
      assertThat(response.get("id").isJsonNull()).isTrue();
    } else {
      assertThat(response.get("id").getAsLong()).isEqualTo(id);
    }
    assertThat(response.keySet()).containsExactly("id", "exitCode", "stdout", "stderr");
    assertThat(response.get("exitCode").getAsInt()).isEqualTo(2);
    assertThat(response.get("stdout").getAsString()).isEmpty();
    assertThat(response.get("stderr").getAsString()).isNotBlank();
  }

  /** Ответ совпадает с разовым запуском тех же аргументов по коду выхода и stdout. */
  private static void assertSameAsOneShot(JsonObject response, String... args) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintStream savedOut = System.out;
    PrintStream savedErr = System.err;
    int exit;
    try {
      System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute(args);
    } finally {
      System.setOut(savedOut);
      System.setErr(savedErr);
    }
    assertThat(response.get("exitCode").getAsInt()).isEqualTo(exit);
    assertThat(response.get("stdout").getAsString()).isEqualTo(stdout.toString(StandardCharsets.UTF_8));
  }

  private Path copyLanguageFixture(String name) throws IOException {
    Path target = tempDir.resolve(name);
    try (Stream<Path> files = Files.walk(LANGUAGE_FIXTURE)) {
      for (Path file : files.toList()) {
        Path copy = target.resolve(LANGUAGE_FIXTURE.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(copy);
        } else {
          Files.copy(file, copy);
        }
      }
    }
    return target;
  }

  private static void replaceInFile(Path file, String from, String to) throws IOException {
    String text = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(text).contains(from);
    Files.writeString(file, text.replace(from, to), StandardCharsets.UTF_8);
  }

  static String requestLine(long id, Path cwd, String... args) {
    JsonObject request = new JsonObject();
    request.addProperty("id", id);
    JsonArray list = new JsonArray();
    for (String arg : args) {
      list.add(arg);
    }
    request.add("args", list);
    if (cwd != null) {
      request.addProperty("cwd", cwd.toString());
    }
    return request.toString();
  }

  private static String json(String value) {
    return new com.google.gson.JsonPrimitive(value).toString();
  }

  /** Сеанс в отдельном потоке: запросы идут в трубу, ответы собираются по строкам. */
  private static final class Client {

    private final PipedOutputStream requests = new PipedOutputStream();
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final FutureTask<Integer> session;

    Client() throws IOException {
      PipedInputStream input = new PipedInputStream(requests, 1 << 16);
      PrintStream log = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
      ServeSession serve = new ServeSession(input, new LineSink(lines), log);
      session = new FutureTask<>(serve::run);
      Thread thread = new Thread(session, "serve session test");
      thread.setDaemon(true);
      thread.start();
    }

    void send(String line) throws IOException {
      requests.write((line + "\n").getBytes(StandardCharsets.UTF_8));
      requests.flush();
    }

    JsonObject request(long id, String... args) throws Exception {
      send(requestLine(id, null, args));
      JsonObject response = next();
      assertThat(response.get("id").getAsLong()).isEqualTo(id);
      return response;
    }

    JsonObject next() throws InterruptedException {
      String line = lines.poll(120, TimeUnit.SECONDS);
      assertThat(line).as("ответ сеанса").isNotNull();
      return JsonParser.parseString(line).getAsJsonObject();
    }

    /** Закрывает ввод и ждёт выхода сеанса. */
    int finish() throws Exception {
      try {
        requests.close();
      } catch (IOException ignored) {
        // Сеанс уже закрыл свою сторону
      }
      return awaitExit();
    }

    int awaitExit() throws Exception {
      return session.get(120, TimeUnit.SECONDS);
    }
  }

  /** Вывод сеанса по строкам. */
  private static final class LineSink extends OutputStream {

    private final BlockingQueue<String> lines;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();

    LineSink(BlockingQueue<String> lines) {
      this.lines = lines;
    }

    @Override
    public synchronized void write(int b) {
      if (b == '\n') {
        lines.add(line.toString(StandardCharsets.UTF_8));
        line.reset();
      } else {
        line.write(b);
      }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
      int start = offset;
      int end = offset + length;
      for (int i = offset; i < end; i++) {
        if (bytes[i] == '\n') {
          line.write(bytes, start, i - start);
          lines.add(line.toString(StandardCharsets.UTF_8));
          line.reset();
          start = i + 1;
        }
      }
      line.write(bytes, start, end - start);
    }
  }
}
