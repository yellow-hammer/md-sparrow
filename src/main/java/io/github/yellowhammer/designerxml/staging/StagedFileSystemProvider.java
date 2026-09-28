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
package io.github.yellowhammer.designerxml.staging;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Провайдер промежуточной файловой системы.
 *
 * <p>Изменения (записанные файлы, созданные каталоги, удаления) живут в памяти,
 * остальное читается с диска. Всё, что операция узнала о диске, запоминается:
 * вид пути, отпечаток прочитанного содержимого, состав прочитанного каталога.
 * Публикация сначала сверяет эти наблюдения с диском и пишет, только если он
 * с тех пор не изменился; при сбое записи уже сделанное откатывается.
 */
final class StagedFileSystemProvider extends FileSystemProvider {

  /** Вид пути на диске или в промежуточном состоянии. */
  enum Kind { ABSENT, FILE, DIRECTORY, OTHER }

  /** Промежуточное состояние пути. */
  sealed interface Entry permits StagedFile, StagedDirectory, Deleted {
  }

  /** Файл с новым содержимым. */
  record StagedFile(byte[] bytes, FileTime modified) implements Entry {
  }

  /** Созданный каталог. */
  record StagedDirectory(FileTime modified) implements Entry {
  }

  /** Удалённый файл или каталог. */
  record Deleted() implements Entry {
  }

  /** Что операция узнала о пути диска при первом обращении. */
  private static final class Observed {
    Kind kind;
    /** Отпечаток содержимого, если файл читался. */
    String digest;
  }

  private static final AtomicLong CLOCK = new AtomicLong();

  private final StagedFileSystem fs = new StagedFileSystem(this);
  private final Map<Path, Entry> staged = new HashMap<>();
  private final Map<Path, Observed> observed = new HashMap<>();
  private final Map<Path, String> listings = new HashMap<>();
  /** Запись файла при публикации; тест подменяет её, чтобы сорвать публикацию посередине. */
  FileWriter writer = StagedFileSystemProvider::writeAtomically;

  /** Запись содержимого файла на диск. */
  @FunctionalInterface
  interface FileWriter {
    void write(Path key, byte[] bytes) throws IOException;
  }

  StagedFileSystem fileSystem() {
    return fs;
  }

  // ---------- состояние ----------

  /** Ключ пути: абсолютный нормализованный путь диска. */
  private static Path key(Path path) {
    if (path instanceof StagedPath staged) {
      return staged.delegate().toAbsolutePath().normalize();
    }
    throw new ProviderMismatchException();
  }

  /** Промежуточное состояние пути, если оно есть. */
  Entry staged(Path key) {
    return staged.get(key);
  }

  /** Вид пути с учётом промежуточных изменений. */
  private Kind kind(Path key) throws IOException {
    Entry entry = staged.get(key);
    if (entry instanceof StagedFile) {
      return Kind.FILE;
    }
    if (entry instanceof StagedDirectory) {
      return Kind.DIRECTORY;
    }
    if (entry instanceof Deleted) {
      return Kind.ABSENT;
    }
    return observeKind(key);
  }

  /** Вид пути на диске: запоминается при первом обращении. */
  private Kind observeKind(Path key) throws IOException {
    Kind now = diskKind(key);
    observed.computeIfAbsent(key, k -> {
      Observed o = new Observed();
      o.kind = now;
      return o;
    });
    return now;
  }

  private static Kind diskKind(Path key) throws IOException {
    try {
      BasicFileAttributes attributes = Files.readAttributes(key, BasicFileAttributes.class);
      if (attributes.isRegularFile()) {
        return Kind.FILE;
      }
      return attributes.isDirectory() ? Kind.DIRECTORY : Kind.OTHER;
    } catch (NoSuchFileException e) {
      return Kind.ABSENT;
    }
  }

  /** Содержимое файла с учётом промежуточных изменений. */
  private byte[] bytes(Path key) throws IOException {
    Entry entry = staged.get(key);
    if (entry instanceof StagedFile file) {
      return file.bytes();
    }
    if (entry instanceof Deleted) {
      throw new NoSuchFileException(key.toString());
    }
    if (entry instanceof StagedDirectory) {
      throw new FileSystemException(key.toString(), null, "это каталог");
    }
    Kind kind = observeKind(key);
    if (kind == Kind.ABSENT) {
      throw new NoSuchFileException(key.toString());
    }
    if (kind == Kind.DIRECTORY) {
      throw new FileSystemException(key.toString(), null, "это каталог");
    }
    byte[] bytes = Files.readAllBytes(key);
    Observed o = observed.get(key);
    if (o.digest == null) {
      o.digest = digest(bytes);
    }
    return bytes;
  }

  private void requireDirectory(Path key) throws IOException {
    if (key != null && kind(key) != Kind.DIRECTORY) {
      throw new NoSuchFileException(key.toString());
    }
  }

  private void putFile(Path key, byte[] bytes) {
    staged.put(key, new StagedFile(bytes, tick()));
  }

  private static FileTime tick() {
    // Время изменения растёт с каждой записью: кэши по размеру и времени видят правку
    long now = System.currentTimeMillis();
    return FileTime.fromMillis(CLOCK.updateAndGet(last -> Math.max(last + 1, now)));
  }

  /** Имена в каталоге с учётом промежуточных изменений. */
  private Set<String> names(Path dirKey) throws IOException {
    if (kind(dirKey) != Kind.DIRECTORY) {
      throw new NoSuchFileException(dirKey.toString());
    }
    Set<String> names = new TreeSet<>();
    if (diskKind(dirKey) == Kind.DIRECTORY) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirKey)) {
        for (Path child : stream) {
          names.add(child.getFileName().toString());
        }
      }
      listings.putIfAbsent(dirKey, digest(String.join("\n", names).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    for (Map.Entry<Path, Entry> entry : staged.entrySet()) {
      Path parent = entry.getKey().getParent();
      if (parent != null && parent.equals(dirKey)) {
        String name = entry.getKey().getFileName().toString();
        if (entry.getValue() instanceof Deleted) {
          names.remove(name);
        } else {
          names.add(name);
        }
      }
    }
    return names;
  }

  // ---------- сверка и публикация ----------

  /**
   * Сверяет с диском всё, что операция о нём узнала.
   *
   * @throws StaleInputException если диск изменился с первого обращения
   */
  void verifyInputs() throws IOException {
    for (Map.Entry<Path, Observed> entry : observed.entrySet()) {
      Path key = entry.getKey();
      Observed o = entry.getValue();
      Kind now = diskKind(key);
      if (now != o.kind) {
        throw new StaleInputException(key);
      }
      if (o.digest != null && !o.digest.equals(digest(Files.readAllBytes(key)))) {
        throw new StaleInputException(key);
      }
    }
    for (Map.Entry<Path, String> entry : listings.entrySet()) {
      Set<String> names = new TreeSet<>();
      if (diskKind(entry.getKey()) == Kind.DIRECTORY) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(entry.getKey())) {
          for (Path child : stream) {
            names.add(child.getFileName().toString());
          }
        }
      }
      if (!entry.getValue().equals(digest(String.join("\n", names).getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
        throw new StaleInputException(entry.getKey());
      }
    }
  }

  /** Пути диска, которые публикация изменит: с отличающимся содержимым или видом. */
  List<Path> changes() throws IOException {
    List<Path> out = new ArrayList<>();
    for (Map.Entry<Path, Entry> entry : sortedStaged()) {
      Path key = entry.getKey();
      Kind disk = diskKind(key);
      Entry value = entry.getValue();
      boolean changed;
      if (value instanceof StagedFile file) {
        changed = disk != Kind.FILE || !Arrays.equals(file.bytes(), Files.readAllBytes(key));
      } else if (value instanceof StagedDirectory) {
        changed = disk != Kind.DIRECTORY;
      } else {
        changed = disk != Kind.ABSENT;
      }
      if (changed) {
        out.add(key);
      }
    }
    return out;
  }

  private List<Map.Entry<Path, Entry>> sortedStaged() {
    List<Map.Entry<Path, Entry>> entries = new ArrayList<>(staged.entrySet());
    entries.sort(Map.Entry.comparingByKey());
    return entries;
  }

  /**
   * Пишет изменения на диск: сначала сверка входов, затем удаления, каталоги и
   * файлы. Если запись сорвалась, всё уже сделанное откатывается в обратном порядке.
   */
  void publish() throws IOException {
    verifyInputs();
    List<Path> removals = new ArrayList<>();
    List<Path> directories = new ArrayList<>();
    Map<Path, byte[]> files = new LinkedHashMap<>();
    for (Map.Entry<Path, Entry> entry : sortedStaged()) {
      Path key = entry.getKey();
      Kind disk = diskKind(key);
      Entry value = entry.getValue();
      if (value instanceof Deleted) {
        if (disk != Kind.ABSENT) {
          removals.add(key);
        }
      } else if (value instanceof StagedDirectory) {
        if (disk == Kind.FILE) {
          removals.add(key);
        }
        if (disk != Kind.DIRECTORY) {
          directories.add(key);
        }
      } else if (value instanceof StagedFile file) {
        if (disk == Kind.DIRECTORY) {
          removals.add(key);
        }
        if (disk != Kind.FILE || !Arrays.equals(file.bytes(), Files.readAllBytes(key))) {
          files.put(key, file.bytes());
        }
      }
    }
    // Вложенное удаляется раньше каталога, каталоги создаются от корня
    removals.sort(Comparator.comparingInt(Path::getNameCount).reversed());
    directories.sort(Comparator.comparingInt(Path::getNameCount));

    List<Undo> journal = new ArrayList<>();
    try {
      for (Path key : removals) {
        if (Files.isDirectory(key, LinkOption.NOFOLLOW_LINKS)) {
          Files.delete(key);
          journal.add(() -> Files.createDirectory(key));
        } else {
          byte[] original = Files.readAllBytes(key);
          Files.delete(key);
          journal.add(() -> writeAtomically(key, original));
        }
      }
      for (Path key : directories) {
        Files.createDirectory(key);
        journal.add(() -> Files.delete(key));
      }
      for (Map.Entry<Path, byte[]> file : files.entrySet()) {
        Path key = file.getKey();
        byte[] original = Files.isRegularFile(key) ? Files.readAllBytes(key) : null;
        writer.write(key, file.getValue());
        journal.add(original == null ? () -> Files.delete(key) : () -> writeAtomically(key, original));
      }
    } catch (IOException | RuntimeException failure) {
      for (int i = journal.size() - 1; i >= 0; i--) {
        try {
          journal.get(i).run();
        } catch (IOException | RuntimeException undo) {
          failure.addSuppressed(undo);
        }
      }
      throw new IOException("Изменения не записаны, диск возвращён в прежнее состояние: " + failure.getMessage(), failure);
    }
  }

  @FunctionalInterface
  private interface Undo {
    void run() throws IOException;
  }

  /** Запись через временный файл рядом: читающий никогда не видит файл наполовину. */
  private static void writeAtomically(Path key, byte[] bytes) throws IOException {
    Path temp = key.resolveSibling("." + key.getFileName() + "." + UUID.randomUUID() + ".tmp");
    Files.write(temp, bytes);
    try {
      try {
        Files.move(temp, key, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, key, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static String digest(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  // ---------- FileSystemProvider ----------

  @Override
  public String getScheme() {
    return "md-sparrow-staged";
  }

  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileSystem getFileSystem(URI uri) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path getPath(URI uri) {
    return fs.wrap(Path.of(uri));
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
      throws IOException {
    Path key = key(path);
    boolean append = options.contains(StandardOpenOption.APPEND);
    boolean write = append || options.contains(StandardOpenOption.WRITE);
    if (!write) {
      return new MemoryChannel(bytes(key), false, false, null);
    }
    Kind kind = kind(key);
    if (kind == Kind.DIRECTORY) {
      throw new FileSystemException(key.toString(), null, "это каталог");
    }
    boolean exists = kind != Kind.ABSENT;
    if (exists && options.contains(StandardOpenOption.CREATE_NEW)) {
      throw new FileAlreadyExistsException(key.toString());
    }
    if (!exists && !options.contains(StandardOpenOption.CREATE) && !options.contains(StandardOpenOption.CREATE_NEW)) {
      throw new NoSuchFileException(key.toString());
    }
    requireDirectory(key.getParent());
    byte[] initial = exists && !options.contains(StandardOpenOption.TRUNCATE_EXISTING) ? bytes(key) : new byte[0];
    putFile(key, initial);
    return new MemoryChannel(initial, true, append, content -> putFile(key, content));
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
      throws IOException {
    List<Path> children = new ArrayList<>();
    for (String name : names(key(dir))) {
      Path child = dir.resolve(name);
      if (filter == null || filter.accept(child)) {
        children.add(child);
      }
    }
    return new DirectoryStream<>() {
      @Override
      public Iterator<Path> iterator() {
        return children.iterator();
      }

      @Override
      public void close() {
      }
    };
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    Path key = key(dir);
    if (kind(key) != Kind.ABSENT) {
      throw new FileAlreadyExistsException(key.toString());
    }
    requireDirectory(key.getParent());
    staged.put(key, new StagedDirectory(tick()));
  }

  @Override
  public void delete(Path path) throws IOException {
    Path key = key(path);
    Kind kind = kind(key);
    if (kind == Kind.ABSENT) {
      throw new NoSuchFileException(key.toString());
    }
    if (kind == Kind.DIRECTORY && !names(key).isEmpty()) {
      throw new DirectoryNotEmptyException(key.toString());
    }
    if (observeKind(key) == Kind.ABSENT) {
      staged.remove(key);
    } else {
      staged.put(key, new Deleted());
    }
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    Path from = key(source);
    Path to = key(target);
    if (from.equals(to)) {
      return;
    }
    Kind kind = kind(from);
    if (kind == Kind.ABSENT) {
      throw new NoSuchFileException(from.toString());
    }
    replaceTarget(target, to, options);
    if (kind == Kind.DIRECTORY) {
      createDirectory(target);
    } else {
      requireDirectory(to.getParent());
      putFile(to, bytes(from));
    }
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    Path from = key(source);
    Path to = key(target);
    if (from.equals(to)) {
      return;
    }
    Kind kind = kind(from);
    if (kind == Kind.ABSENT) {
      throw new NoSuchFileException(from.toString());
    }
    if (to.startsWith(from)) {
      throw new FileSystemException(from.toString(), to.toString(), "каталог нельзя перенести в самого себя");
    }
    replaceTarget(target, to, options);
    if (kind == Kind.DIRECTORY) {
      // Каталог переносится со всем содержимым, как переименование на диске
      createDirectory(target);
      for (String name : names(from)) {
        move(source.resolve(name), target.resolve(name));
      }
    } else {
      requireDirectory(to.getParent());
      putFile(to, bytes(from));
    }
    delete(source);
  }

  private void replaceTarget(Path target, Path to, CopyOption... options) throws IOException {
    if (kind(to) == Kind.ABSENT) {
      return;
    }
    if (!Arrays.asList(options).contains(StandardCopyOption.REPLACE_EXISTING)) {
      throw new FileAlreadyExistsException(to.toString());
    }
    delete(target);
  }

  @Override
  public boolean isSameFile(Path path, Path other) throws IOException {
    return other instanceof StagedPath && key(path).equals(key(other));
  }

  @Override
  public boolean isHidden(Path path) {
    Path name = key(path).getFileName();
    return name != null && name.toString().startsWith(".");
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException {
    Path existing = key(path);
    while (existing != null && diskKind(existing) == Kind.ABSENT) {
      existing = existing.getParent();
    }
    if (existing == null) {
      throw new NoSuchFileException(path.toString());
    }
    return Files.getFileStore(existing);
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    if (kind(key(path)) == Kind.ABSENT) {
      throw new NoSuchFileException(path.toString());
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
    if (type != BasicFileAttributeView.class) {
      return null;
    }
    return (V) new BasicFileAttributeView() {
      @Override
      public String name() {
        return "basic";
      }

      @Override
      public BasicFileAttributes readAttributes() throws IOException {
        return StagedFileSystemProvider.this.readAttributes(path, BasicFileAttributes.class, options);
      }

      @Override
      public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
        // Время файла промежуточная система не хранит: публикация ставит своё
      }
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
      throws IOException {
    Path key = key(path);
    Entry entry = staged.get(key);
    if (entry == null) {
      Kind kind = observeKind(key);
      if (kind == Kind.ABSENT) {
        throw new NoSuchFileException(key.toString());
      }
      return Files.readAttributes(key, type, options);
    }
    if (!type.isAssignableFrom(BasicFileAttributes.class)) {
      throw new UnsupportedOperationException("у промежуточного файла есть только базовые атрибуты");
    }
    if (entry instanceof Deleted) {
      throw new NoSuchFileException(key.toString());
    }
    return (A) new Attributes(entry);
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
    String view = attributes.contains(":") ? attributes.substring(0, attributes.indexOf(':')) : "basic";
    if (!view.equals("basic")) {
      Path key = key(path);
      if (staged.get(key) != null) {
        throw new UnsupportedOperationException("у промежуточного файла есть только базовые атрибуты");
      }
      observeKind(key);
      return Files.readAttributes(key, attributes, options);
    }
    BasicFileAttributes basic = readAttributes(path, BasicFileAttributes.class, options);
    Map<String, Object> all = new LinkedHashMap<>();
    all.put("lastModifiedTime", basic.lastModifiedTime());
    all.put("lastAccessTime", basic.lastAccessTime());
    all.put("creationTime", basic.creationTime());
    all.put("size", basic.size());
    all.put("isRegularFile", basic.isRegularFile());
    all.put("isDirectory", basic.isDirectory());
    all.put("isSymbolicLink", basic.isSymbolicLink());
    all.put("isOther", basic.isOther());
    all.put("fileKey", basic.fileKey());
    String names = attributes.contains(":") ? attributes.substring(attributes.indexOf(':') + 1) : attributes;
    if (names.equals("*")) {
      return all;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (String name : names.split(",")) {
      if (all.containsKey(name)) {
        out.put(name, all.get(name));
      }
    }
    return out;
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
    throw new UnsupportedOperationException("атрибуты промежуточной файловой системы не меняются");
  }

  /** Атрибуты промежуточного файла или каталога. */
  private record Attributes(Entry entry) implements BasicFileAttributes {

    private FileTime time() {
      return entry instanceof StagedFile file ? file.modified() : ((StagedDirectory) entry).modified();
    }

    @Override
    public FileTime lastModifiedTime() {
      return time();
    }

    @Override
    public FileTime lastAccessTime() {
      return time();
    }

    @Override
    public FileTime creationTime() {
      return time();
    }

    @Override
    public boolean isRegularFile() {
      return entry instanceof StagedFile;
    }

    @Override
    public boolean isDirectory() {
      return entry instanceof StagedDirectory;
    }

    @Override
    public boolean isSymbolicLink() {
      return false;
    }

    @Override
    public boolean isOther() {
      return false;
    }

    @Override
    public long size() {
      return entry instanceof StagedFile file ? file.bytes().length : 0;
    }

    @Override
    public Object fileKey() {
      return null;
    }
  }

  /** Канал над массивом в памяти; записывающий по закрытию отдаёт содержимое. */
  private static final class MemoryChannel implements SeekableByteChannel {

    private byte[] buffer;
    private int size;
    private long position;
    private boolean open = true;
    private final boolean writable;
    private final boolean append;
    private final java.util.function.Consumer<byte[]> onClose;

    MemoryChannel(byte[] initial, boolean writable, boolean append, java.util.function.Consumer<byte[]> onClose) {
      this.buffer = initial.clone();
      this.size = initial.length;
      this.writable = writable;
      this.append = append;
      this.onClose = onClose;
    }

    private void ensureOpen() throws ClosedChannelException {
      if (!open) {
        throw new ClosedChannelException();
      }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      ensureOpen();
      if (position >= size) {
        return -1;
      }
      int count = (int) Math.min(dst.remaining(), size - position);
      dst.put(buffer, (int) position, count);
      position += count;
      return count;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      ensureOpen();
      if (!writable) {
        throw new NonWritableChannelException();
      }
      if (append) {
        position = size;
      }
      int count = src.remaining();
      long end = position + count;
      if (end > Integer.MAX_VALUE) {
        throw new IOException("файл слишком велик для промежуточной записи");
      }
      if (end > buffer.length) {
        buffer = Arrays.copyOf(buffer, (int) Math.max(end, buffer.length * 2L));
      }
      src.get(buffer, (int) position, count);
      position = end;
      size = (int) Math.max(size, end);
      return count;
    }

    @Override
    public long position() {
      return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) {
      position = newPosition;
      return this;
    }

    @Override
    public long size() {
      return size;
    }

    @Override
    public SeekableByteChannel truncate(long newSize) {
      if (!writable) {
        throw new NonWritableChannelException();
      }
      size = (int) Math.min(size, newSize);
      position = Math.min(position, size);
      return this;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void close() {
      if (open && onClose != null) {
        onClose.accept(Arrays.copyOf(buffer, size));
      }
      open = false;
    }
  }
}
