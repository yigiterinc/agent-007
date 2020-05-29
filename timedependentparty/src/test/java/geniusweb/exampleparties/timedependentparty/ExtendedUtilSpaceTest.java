package geniusweb.exampleparties.timedependentparty;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.ObjectMapper;

import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditiveUtilitySpace;

@RunWith(Parameterized.class)
public class ExtendedUtilSpaceTest {
	private final static ObjectMapper jackson = new ObjectMapper();
	private final static BigDecimal SMALL = new BigDecimal("0.0001");

	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
				{ "src/test/resources/jobs/jobs1.json", 0.02 },
				{ "src/test/resources/7issues/7issues1.json", 0.0055 },
				{ "src/test/resources/9issues/9issues1.json", 0.0013 } });
	}

	private String filename;
	private BigDecimal expectedTolerance;
	private ExtendedUtilSpace space;

	public ExtendedUtilSpaceTest(String filename, double expectedTolerance) {
		this.filename = filename;
		this.expectedTolerance = BigDecimal.valueOf(expectedTolerance);
	}

	@Before
	public void before() throws IOException {
		String file = new String(Files.readAllBytes(Paths.get(filename)));
		LinearAdditiveUtilitySpace profile = (LinearAdditiveUtilitySpace) jackson
				.readValue(file, Profile.class);
		space = new ExtendedUtilSpace(profile);
	}

	@Test
	public void smokeTest() {

	}

	@Test
	public void testTolerance() {
		assertTrue(space.computeTolerance().subtract(expectedTolerance).abs()
				.compareTo(SMALL) < 0);
	}
}
