package geniusweb.exampleparties.humangui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import geniusweb.actions.EndNegotiation;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Finished;
import geniusweb.party.inform.Settings;
import geniusweb.party.inform.YourTurn;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;
import geniusweb.progress.ProgressRounds;
import geniusweb.references.Parameters;
import geniusweb.references.ProfileRef;
import geniusweb.references.ProtocolRef;

/**
 * Should test the GUI stuff. At this moment it's just a simple quick test of
 * the GUI without much testing
 *
 */
public class HumanGuiTest {

	private static final String SAOP = "SAOP";
	private static final PartyId otherparty = new PartyId("other");
	private static final String PROFILE = "src/test/resources/testprofile.json";
	private final static ObjectMapper jackson = new ObjectMapper();

	private HumanGui party;
	private final TestConnection connection = new TestConnection();
	private final ProtocolRef protocol = mock(ProtocolRef.class);
	private final ProgressRounds progress = mock(ProgressRounds.class);
	private Settings settings;
	private LinearAdditive profile;
	private final Parameters parameters = new Parameters();

	@Before
	public void before() throws JsonParseException, JsonMappingException,
			IOException, URISyntaxException {
		party = new HumanGui();
		settings = new Settings(new PartyId("party1"),
				new ProfileRef(new URI("file:" + PROFILE)), protocol, progress,
				parameters);

		String serialized = new String(Files.readAllBytes(Paths.get(PROFILE)),
				StandardCharsets.UTF_8);
		profile = (LinearAdditive) jackson.readValue(serialized, Profile.class);

	}

	@After
	public void after() {
		party.notifyChange(new Finished(null));
	}

	@Test
	public void smokeTest() {
	}

	@Test
	public void getDescriptionTest() {
		assertNotNull(party.getDescription());
	}

	@Test
	public void getCapabilitiesTest() {
		Capabilities capabilities = party.getCapabilities();
		assertFalse("party does not define protocols",
				capabilities.getBehaviours().isEmpty());
	}

	@Test
	public void testGetCapabilities() {
		assertTrue(party.getCapabilities().getBehaviours().contains(SAOP));
	}

	@Test
	public void testInformConnection() {
		party.connect(connection);
		// agent should not start acting just after an inform
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testInformSettings() throws InterruptedException {
		party.connect(connection);
		connection.notifyListeners(settings);
		assertEquals(0, connection.getActions().size());
		Thread.sleep(1000);
	}

	@Test
	public void testOtherWalksAway() throws InterruptedException {
		party.connect(connection);
		party.notifyChange(settings);

		party.notifyChange(new ActionDone(new EndNegotiation(otherparty)));

		// party should not act at this point
		assertEquals(0, connection.getActions().size());

		Thread.sleep(1000);

	}

	@Test
	public void testReceiveOtherBid() throws InterruptedException {
		party.connect(connection);
		party.notifyChange(settings);
		Thread.sleep(1000);

		AllBidsList allbids = new AllBidsList(profile.getDomain());
		Random r = new Random();
		party.notifyChange(new ActionDone(new Offer(otherparty,
				allbids.get(r.nextInt(allbids.size().intValue())))));

		// party should not act at this point
		assertEquals(0, connection.getActions().size());

		Thread.sleep(1000);

	}

	@Test
	public void testRecvAndYourTurn() throws InterruptedException {
		party.connect(connection);
		party.notifyChange(settings);

		party.notifyChange(new ActionDone(new Offer(otherparty, randomBid())));
		party.notifyChange(new YourTurn());

		Thread.sleep(1000);

	}

	private Bid randomBid() {
		AllBidsList allbids = new AllBidsList(profile.getDomain());
		Random r = new Random();
		return allbids.get(r.nextInt(allbids.size().intValue()));

	}

}
