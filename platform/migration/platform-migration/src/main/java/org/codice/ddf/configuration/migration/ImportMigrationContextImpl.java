/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.configuration.migration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import ddf.security.common.audit.SecurityLogger;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;
import org.codice.ddf.migration.ImportMigrationContext;
import org.codice.ddf.migration.ImportMigrationEntry;
import org.codice.ddf.migration.Migratable;
import org.codice.ddf.migration.MigrationException;
import org.codice.ddf.migration.MigrationReport;
import org.codice.ddf.migration.MigrationWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The import migration context keeps track of exported migration entries for a given migratable
 * while processing an import migration operation.
 */
@SuppressWarnings("squid:S2160" /* the base class equals() is sufficient for our needs */)
public class ImportMigrationContextImpl extends MigrationContextImpl<MigrationReport>
    implements ImportMigrationContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportMigrationContextImpl.class);

  private static final String INVALID_NULL_ZIP = "invalid null zip";

  private static final String INVALID_NULL_PATH = "invalid null path";

  /** Holds exported migration entries keyed by the exported path. */
  private final Map<Path, ImportMigrationEntryImpl> entries = new TreeMap<>();

  /** Holds migration entries referenced from system properties keyed by the property name. */
  private final Map<String, ImportMigrationSystemPropertyReferencedEntryImpl> systemProperties =
      new TreeMap<>();

  private final Set<String> files = new HashSet<>();

  private final ZipFile zip;

  private final List<InputStream> inputStreams = new ArrayList<>();

  /**
   * Creates a new migration context for an import operation representing a system context.
   *
   * @param report the migration report where warnings and errors can be recorded
   * @param zip the zip file associated with the import
   * @throws IllegalArgumentException if <code>report</code> or <code>zip</code> is <code>null
   * </code>
   * @throws java.io.IOError if unable to determine ${ddf.home}
   */
  public ImportMigrationContextImpl(MigrationReport report, ZipFile zip) {
    super(report);
    Validate.notNull(zip, ImportMigrationContextImpl.INVALID_NULL_ZIP);
    this.zip = zip;
  }

  /**
   * Creates a new migration context for an import operation.
   *
   * @param report the migration report where warnings and errors can be recorded
   * @param zip the zip file associated with the import
   * @param id the migratable id
   * @throws IllegalArgumentException if <code>report</code>, <code>zip</code>, or <code>id</code>
   *     is <code>null</code>
   * @throws java.io.IOError if unable to determine ${ddf.home}
   */
  public ImportMigrationContextImpl(MigrationReport report, ZipFile zip, String id) {
    super(report, id);
    Validate.notNull(zip, ImportMigrationContextImpl.INVALID_NULL_ZIP);
    this.zip = zip;
  }

  /**
   * Creates a new migration context for an import operation.
   *
   * @param report the migration report where warnings and errors can be recorded
   * @param zip the zip file associated with the import
   * @param migratable the migratable this context is for
   * @throws IllegalArgumentException if <code>report</code>, <code>zip</code> or <code>migratable
   * </code> is <code>null</code>
   * @throws java.io.IOError if unable to determine ${ddf.home}
   */
  public ImportMigrationContextImpl(MigrationReport report, ZipFile zip, Migratable migratable) {
    super(report, migratable);
    Validate.notNull(zip, ImportMigrationContextImpl.INVALID_NULL_ZIP);
    this.zip = zip;
  }

  @Override
  public Optional<ImportMigrationEntry> getSystemPropertyReferencedEntry(String name) {
    Validate.notNull(name, "invalid null system property name");
    return Optional.ofNullable(systemProperties.get(name));
  }

  @Override
  public ImportMigrationEntry getEntry(Path path) {
    Validate.notNull(path, ImportMigrationContextImpl.INVALID_NULL_PATH);
    return entries.computeIfAbsent(path, p -> new ImportMigrationEmptyEntryImpl(this, p));
  }

  @Override
  public Stream<ImportMigrationEntry> entries() {
    return entries.values().stream().map(ImportMigrationEntry.class::cast);
  }

  @Override
  public Stream<ImportMigrationEntry> entries(Path path) {
    Validate.notNull(path, ImportMigrationContextImpl.INVALID_NULL_PATH);
    return entries().filter(me -> me.getPath().startsWith(path));
  }

  @Override
  public Stream<ImportMigrationEntry> entries(Path path, PathMatcher filter) {
    Validate.notNull(filter, "invalid null filter");
    return entries(path).filter(e -> filter.matches(e.getPath()));
  }

  @Override
  public boolean cleanDirectory(Path path) {
    Validate.notNull(path, ImportMigrationContextImpl.INVALID_NULL_PATH);
    final Path rpath = getPathUtils().resolveAgainstDDFHome(path);
    final File fdir = rpath.toFile();

    LOGGER.debug("Cleaning up directory [{}]...", fdir);
    try {
      if (!AccessUtils.doPrivileged(
          () -> getPathUtils().isRelativeToDDFHome(rpath.toRealPath(LinkOption.NOFOLLOW_LINKS)))) {
        LOGGER.info("Failed to clean directory [{}]", fdir);
        getReport()
            .record(
                new MigrationWarning(
                    Messages.IMPORT_PATH_CLEAN_WARNING,
                    path,
                    String.format("not relative to [%s]", getPathUtils().getDDFHome())));
        return false;
      }
    } catch (NoSuchFileException e) {
      return true;
    } catch (IOException e) {
      LOGGER.info("Failed to clean directory [" + fdir + "]: ", e);
      getReport().record(new MigrationWarning(Messages.IMPORT_PATH_CLEAN_WARNING, path, e));
      return false;
    }
    if (!fdir.exists()) {
      return true;
    }
    if (!fdir.isDirectory()) {
      LOGGER.info("Failed to clean directory [{}]", fdir);
      getReport()
          .record(
              new MigrationWarning(Messages.IMPORT_PATH_CLEAN_WARNING, path, "not a directory"));
      return false;
    }
    try {
      ImportMigrationContextImpl.cleanDirectory(fdir);
      SecurityLogger.audit("Deleted content of directory {}", fdir);
    } catch (IOException e) {
      SecurityLogger.audit("Error deleting content of directory {}", fdir);
      LOGGER.info("Failed to clean directory [" + fdir + "]: ", e);
      getReport().record(new MigrationWarning(Messages.IMPORT_PATH_CLEAN_WARNING, path, e));
      return false;
    }
    return true;
  }

  /**
   * Temporary version of cleanDirectory() that doesn't delete any directories but only files found.
   * This is to work around a directory handle issue on Windows which leaves the directory in a
   * weird state and prevents us from recreating it.
   *
   * <p>Cleans a directory without deleting it.
   *
   * @param directory directory to clean
   * @throws IOException in case cleaning is unsuccessful
   */
  private static void cleanDirectory(final File directory) throws IOException {
    final File[] files = directory.listFiles();

    if (files == null) { // null if security restricted
      throw new IOException("failed to list contents of " + directory);
    }
    IOException ioe = null;

    for (final File file : files) {
      try {
        if (file.isDirectory()) {
          ImportMigrationContextImpl.cleanDirectory(file);
        } else {
          final boolean exists = file.exists();

          if (!file.delete()) {
            if (!exists) {
              throw new FileNotFoundException("file does not exist: " + file);
            }
            throw new IOException("unable to delete file: " + file);
          }
        }
      } catch (IOException e) {
        ioe = e;
      }
    }
    if (ioe != null) {
      throw ioe;
    }
  }

  @SuppressWarnings(
      "PMD.DefaultPackage" /* designed to be called from ImportMigrationPropertyReferencedEntryImpl within this package */)
  @VisibleForTesting
  Optional<ImportMigrationEntryImpl> getOptionalEntry(Path path) {
    return Optional.ofNullable(entries.get(path));
  }

  /**
   * Performs an import using the context's migratable.
   *
   * @throws org.codice.ddf.migration.MigrationException to stop the import operation
   */
  @SuppressWarnings(
      "PMD.DefaultPackage" /* designed to be called from ImportMigrationManagerImpl within this package */)
  void doImport() {
    if (migratable != null) {
      LOGGER.debug("Importing migratable [{}] from version [{}]...", id, getVersion());
      Stopwatch stopwatch = null;

      if (LOGGER.isDebugEnabled()) {
        stopwatch = Stopwatch.createStarted();
      }
      try {
        final String version = getVersion().orElse(null);

        if (version == null) {
          migratable.doMissingImport(this);
        } else if (version.equals(migratable.getVersion())) {
          migratable.doImport(this);
        } else {
          migratable.doIncompatibleImport(this, version);
        }
      } finally {
        inputStreams.forEach(IOUtils::closeQuietly); // we do not care if we failed to close them
      }
      if (LOGGER.isDebugEnabled() && (stopwatch != null)) {
        LOGGER.debug("Imported time for {}: {}", id, stopwatch.stop());
      }
    } else if (id != null) { // not a system context
      LOGGER.warn(
          "unable to import migration data for migratable [{}]; migratable is no longer available",
          id);
      report.record(new MigrationException(Messages.IMPORT_UNKNOWN_DATA_FOUND_ERROR));
    } // else - no errors and nothing to do for the system context
  }

  @SuppressWarnings(
      "PMD.DefaultPackage" /* designed to be called from ImportMigrationManagerImpl within this package */)
  void addEntry(ImportMigrationEntryImpl entry) {
    entries.put(entry.getPath(), entry);
  }

  @SuppressWarnings(
      "PMD.DefaultPackage" /* designed to be called from ImportMigrationEntryImpl within this package */)
  @VisibleForTesting
  InputStream getInputStreamFor(ImportMigrationEntryImpl entry, boolean checkAccess)
      throws IOException {
    if (checkAccess && files.contains(entry.getName())) {
      // we were asked to check if the migratable that is requesting this stream has read access
      // and the content was exported by the framework and not by the migratable directly, as
      // such we need to check with the security manager if one was installed
      final SecurityManager sm = System.getSecurityManager();

      if (sm != null) {
        sm.checkRead(entry.getFile().getPath());
      }
    }
    final InputStream is = zip.getInputStream(entry.getZipEntry());

    inputStreams.add(is);
    return is;
  }

  @SuppressWarnings(
      "PMD.DefaultPackage" /* designed to be called from ImportMigrationEntryImpl within this package */)
  boolean requiresWriteAccess(ImportMigrationEntryImpl entry) {
    // if the framework didn't export that file then write access is required
    return !files.contains(entry.getName());
  }

  @VisibleForTesting
  ZipFile getZip() {
    return zip;
  }

  @VisibleForTesting
  Set<String> getFiles() {
    return files;
  }

  @VisibleForTesting
  Map<Path, ImportMigrationEntryImpl> getEntries() {
    return entries;
  }

  @VisibleForTesting
  Map<String, ImportMigrationSystemPropertyReferencedEntryImpl>
      getSystemPropertiesReferencedEntries() {
    return systemProperties;
  }

  @Override
  protected void processMetadata(Map<String, Object> metadata) {
    LOGGER.debug("Imported metadata for {}: {}", id, metadata);
    super.processMetadata(metadata);
    // process set of exported files by the framework
    JsonUtils.getListFrom(metadata, MigrationContextImpl.METADATA_FILES)
        .stream()
        .map(JsonUtils::convertToMap)
        .map(m -> m.get(MigrationEntryImpl.METADATA_NAME))
        .filter(Objects::nonNull)
        .map(Object::toString)
        .forEach(files::add);
    // process external entries first so we have a complete set of migratable data entries that
    // were exported by a migratable before we start looking at the property references
    JsonUtils.getListFrom(metadata, MigrationContextImpl.METADATA_EXTERNALS)
        .stream()
        .map(JsonUtils::convertToMap)
        .map(m -> new ImportMigrationExternalEntryImpl(this, m))
        .forEach(me -> entries.put(me.getPath(), me));
    // process system property references
    JsonUtils.getListFrom(metadata, MigrationContextImpl.METADATA_SYSTEM_PROPERTIES)
        .stream()
        .map(JsonUtils::convertToMap)
        .map(m -> new ImportMigrationSystemPropertyReferencedEntryImpl(this, m))
        .forEach(me -> systemProperties.put(me.getProperty(), me));
    // process java property references
    JsonUtils.getListFrom(metadata, MigrationContextImpl.METADATA_JAVA_PROPERTIES)
        .stream()
        .map(JsonUtils::convertToMap)
        .map(m -> new ImportMigrationJavaPropertyReferencedEntryImpl(this, m))
        .forEach(
            me ->
                entries.compute(
                    me.getPropertiesPath(),
                    (p, mpe) -> {
                      if (mpe == null) {
                        // create a new empty migration entry as it was not exported out (at least
                        // not by this migratable)!!!!
                        mpe = new ImportMigrationEmptyEntryImpl(this, p);
                      }
                      mpe.addPropertyReferenceEntry(me.getProperty(), me);
                      return mpe;
                    }));
  }
}
