package de.unibremen.informatik.st.libvcs4j;

import de.unibremen.informatik.st.libvcs4j.engine.AbstractIntervalVCSEngine;
import de.unibremen.informatik.st.libvcs4j.filesystem.SingleEngine;
import de.unibremen.informatik.st.libvcs4j.git.GitEngine;
import de.unibremen.informatik.st.libvcs4j.hg.HGEngine;
import de.unibremen.informatik.st.libvcs4j.svn.SVNEngine;
import org.apache.commons.lang3.Validate;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import static org.apache.commons.lang3.Validate.notNull;

/**
 * A generic builder for {@link VCSEngine} instances that has been designed to
 * handle user input values and, thus, is more forgiving with improper
 * parameters. That is, for instance, {@code null} and empty parameter values
 * are mapped to default values.
 */
@SuppressWarnings({"WeakerAccess", "unused", "UnusedReturnValue"})
public class VCSEngineBuilder {

	private enum Engine { SINGLE, SVN, GIT, HG }

	private enum Interval { DATE, REVISION, RANGE, LATEST }

	//////////////////////////////// Defaults /////////////////////////////////

	public static final String DEFAULT_ROOT = "";

	// Only SVN has restrictions regarding the minimum date.
	public static final LocalDateTime DEFAULT_SINCE =
			SVNEngine.MINIMUM_DATETIME;

	// Year 2200 should be sufficient.
	public static final LocalDateTime DEFAULT_UNTIL =
			LocalDateTime.of(2200, 1, 1, 0, 0);

	public final int DEFAULT_START = AbstractIntervalVCSEngine.MIN_START;

	public final int DEFAULT_END = Integer.MAX_VALUE;

	// Cannot be static since every instance needs its own default value!
	private final String defaultTarget = Paths.get(
			System.getProperty("java.io.tmpdir"),          // system tmp dir
			"libvcs4j").toString() +                       // prefix
			UUID.randomUUID().toString().replace("-", ""); // unique id;

	////////////////////////////// Configuration //////////////////////////////

	private String repository;

	private Engine engine = Engine.SINGLE;

	private String root = DEFAULT_ROOT;

	private String target = defaultTarget;

	private String branch = null;

	private Interval interval = Interval.DATE;

	private LocalDateTime since = LocalDateTime.from(DEFAULT_SINCE);

	private LocalDateTime until = LocalDateTime.from(DEFAULT_UNTIL);

	private int start = DEFAULT_START;

	private int end = DEFAULT_END;

	private String from = null;

	private String to = null;

	private ITEngine itEngine = null;

	////////////////////////////// Constructors ///////////////////////////////

	public VCSEngineBuilder(final String pRepository) {
		withRepository(pRepository);
	}

	public static VCSEngineBuilder of(final String pRepository) {
		return new VCSEngineBuilder(pRepository);
	}

	public static VCSEngineBuilder ofGit(final String pRepository) {
		return of(pRepository).withGit();
	}

	public static VCSEngineBuilder ofHG(final String pRepository) {
		return of(pRepository).withHG();
	}

	public static VCSEngineBuilder ofSingle(final String pRepository) {
		return of(pRepository).withSingle();
	}

	public static VCSEngineBuilder ofSVN(final String pRepository) {
		return of(pRepository).withSVN();
	}

	/////////////////////////////// Fluent API ////////////////////////////////

	public VCSEngineBuilder withRepository(final String pRepository) {
		repository = notNull(pRepository).trim();
		return this;
	}

	public VCSEngineBuilder withSingle() {
		engine = Engine.SINGLE;
		return this;
	}

	public VCSEngineBuilder withSVN() {
		engine = Engine.SVN;
		return this;
	}

	public VCSEngineBuilder withGit() {
		engine = Engine.GIT;
		return this;
	}

	public VCSEngineBuilder withHG() {
		engine = Engine.HG;
		return this;
	}

	public VCSEngineBuilder withRoot(final String pRoot) {
		root = pRoot == null ? DEFAULT_ROOT : pRoot.trim();
		return this;
	}

	public VCSEngineBuilder withTarget(final String pTarget) {
		target = pTarget == null ? defaultTarget : pTarget.trim();
		target = target.isEmpty() ? defaultTarget : target;
		return this;
	}

	public VCSEngineBuilder withTarget(final Path pTarget) {
		return withTarget(Optional.ofNullable(pTarget)
				.map(Path::toString)
				.orElse(null));
	}

	public VCSEngineBuilder withBranch(final String pBranch) {
		branch = pBranch;
		return this;
	}

	public VCSEngineBuilder withSince(final LocalDateTime pSince) {
		since = pSince == null || pSince.isBefore(DEFAULT_SINCE)
				? DEFAULT_SINCE
				: pSince;
		interval = Interval.DATE;
		return this;
	}

	public VCSEngineBuilder withSince(final LocalDate pSince) {
		since = pSince == null || pSince.isBefore(DEFAULT_SINCE.toLocalDate())
				? DEFAULT_SINCE
				: parseDateTime(pSince.toString(), DEFAULT_SINCE);
		interval = Interval.DATE;
		return this;
	}

	public VCSEngineBuilder withSince(final String pSince) {
		since = parseDateTime(pSince, DEFAULT_SINCE);
		interval = Interval.DATE;
		return this;
	}

	public VCSEngineBuilder withUntil(final LocalDateTime pUntil) {
		until = pUntil == null || pUntil.isAfter(DEFAULT_UNTIL)
				? DEFAULT_UNTIL
				: pUntil;
		interval = Interval.DATE;
		return this;
	}

	public VCSEngineBuilder withUntil(final LocalDate pUntil) {
		until = pUntil == null || pUntil.isAfter(DEFAULT_UNTIL.toLocalDate())
				? DEFAULT_UNTIL
				: parseDateTime(pUntil.toString(), DEFAULT_UNTIL);
		interval = Interval.DATE;
		return this;
	}

	public VCSEngineBuilder withUntil(final String pUntil) {
		until = parseDateTime(pUntil, DEFAULT_UNTIL);
		interval = Interval.DATE;
		return this;
	}

	public VCSEngineBuilder withFrom(final String pFrom) {
		from = Validate.notEmpty(pFrom);
		interval = Interval.REVISION;
		return this;
	}

	public VCSEngineBuilder withTo(final String pTo) {
		to = Validate.notEmpty(pTo);
		interval = Interval.REVISION;
		return this;
	}

	public VCSEngineBuilder withStart(final int pStart) {
		start = pStart;
		interval = Interval.RANGE;
		return this;
	}

	public VCSEngineBuilder withEnd(final int pEnd) {
		end = pEnd;
		interval = Interval.RANGE;
		return this;
	}

	public VCSEngineBuilder withLatestRevision() {
		interval = Interval.LATEST;
		return this;
	}

	public VCSEngineBuilder withITEngine(final ITEngine pITEngine) {
		itEngine = pITEngine;
		return this;
	}

	@SuppressWarnings("deprecation")
	public VCSEngine build() {
		final VCSEngine vcsEngine;
		if (engine == Engine.SINGLE) {
			vcsEngine = new SingleEngine(Paths.get(repository, root));
		} else {
			Optional<Path> path = Optional.empty();
			try {
				path = Optional.of(Paths.get(repository));
			} catch (final InvalidPathException ignored) {}
			final String repo = path
					.filter(Files::isDirectory)
					.map(r -> "file://" + r)
					.orElse(repository);
			if (engine == Engine.SVN) {
				if (interval == Interval.DATE) {
					vcsEngine = new SVNEngine(
							repo, root,
							Paths.get(target),
							since, until);
				} else if (interval == Interval.REVISION) {
					vcsEngine = new SVNEngine(
							repo, root,
							Paths.get(target),
							from, to);
				} else if (interval == Interval.RANGE) {
					vcsEngine = new SVNEngine(
							repo, root,
							Paths.get(target),
							start, end);
				} else if (interval == Interval.LATEST) {
					vcsEngine = new SVNEngine(
							repo, root,
							Paths.get(target));
				} else {
					throw new IllegalStateException(String.format(
							"Unknown interval '%s'", interval));
				}
			} else if (engine == Engine.GIT) {
				if (interval == Interval.DATE) {
					vcsEngine = new GitEngine(
							repo, root,
							Paths.get(target),
							branch,
							since, until);
				} else if (interval == Interval.REVISION) {
					vcsEngine = new GitEngine(
							repo, root,
							Paths.get(target),
							branch,
							from, to);
				} else if (interval == Interval.RANGE) {
					vcsEngine = new GitEngine(
							repo, root,
							Paths.get(target),
							branch,
							start, end);
				} else if (interval == Interval.LATEST) {
					vcsEngine = new GitEngine(
							repo, root,
							Paths.get(target),
							branch);
				} else {
					throw new IllegalStateException(String.format(
							"Unknown interval '%s'", interval));
				}
			} else if (engine == Engine.HG) {
				if (interval == Interval.DATE) {
					vcsEngine = new HGEngine(
							repo, root,
							Paths.get(target),
							branch,
							since, until);
				} else if (interval == Interval.REVISION) {
					vcsEngine = new HGEngine(
							repo, root,
							Paths.get(target),
							branch,
							from, to);
				} else if (interval == Interval.RANGE) {
					vcsEngine = new HGEngine(
							repo, root,
							Paths.get(target),
							branch,
							start, end);
				} else if (interval == Interval.LATEST) {
					vcsEngine = new HGEngine(
							repo, root,
							Paths.get(target),
							branch);
				} else {
					throw new IllegalStateException(String.format(
							"Unknown interval '%s'", interval));
				}
			} else {
				throw new IllegalStateException(String.format(
						"Unknown VCS engine '%s'", engine));
			}
		}

		// delete temporary directory when shutting down application
		if (target.equals(defaultTarget)) {
			Runtime.getRuntime().addShutdownHook(new DeleteTask(target));
		}

		if (itEngine != null) {
			vcsEngine.setITEngine(itEngine);
		}
		return vcsEngine;
	}

	///////////////////////////////// Helper //////////////////////////////////

	private LocalDateTime parseDateTime(
			final String pDateTime,
			final LocalDateTime pDefault) {
		if (pDateTime == null) {
			return pDefault;
		}
		final String tmp = pDateTime.trim();
		if (pDateTime.isEmpty()) {
			return pDefault;
		}
		try {
			return LocalDateTime.parse(tmp);
		} catch (final DateTimeParseException ignored) {
		}
		try {
			final LocalDate date = LocalDate.parse(tmp);
			return LocalDateTime.of(
					date.getYear(),
					date.getMonth(),
					date.getDayOfMonth(),
					0, 0);
		}catch (final DateTimeParseException ignored) {
		}
		try {
			final DateFormat dateFormat = new SimpleDateFormat();
			final LocalDateTime dateTime = LocalDateTime.ofInstant(
					dateFormat.parse(pDateTime).toInstant(),
					ZoneId.systemDefault());
		} catch (final ParseException ignored) {
		}
		try {
			final int year = Integer.parseInt(pDateTime);
			return LocalDateTime.of(year, 0, 0, 0, 0);
		} catch (final NumberFormatException |
				DateTimeParseException ignored) {
		}
		throw new IllegalArgumentException(String.format(
				"Unable to parse '%s'", pDateTime));
	}
}
