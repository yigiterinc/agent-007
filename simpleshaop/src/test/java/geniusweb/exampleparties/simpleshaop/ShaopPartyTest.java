package geniusweb.exampleparties.simpleshaop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.ElicitComparison;
import geniusweb.actions.EndNegotiation;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.connection.ConnectionEnd;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.DiscreteValue;
import geniusweb.issuevalue.Value;
import geniusweb.party.Capabilities;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Finished;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.party.inform.YourTurn;
import geniusweb.profile.Profile;
import geniusweb.progress.ProgressRounds;
import geniusweb.references.Parameters;
import geniusweb.references.ProfileRef;
import geniusweb.references.ProtocolRef;
import geniusweb.references.Reference;
import tudelft.utilities.listener.DefaultListenable;
import tudelft.utilities.logging.Reporter;

public class ShaopPartyTest {

	private static final String SHAOP = "SHAOP";
	private static final PartyId otherparty = new PartyId("other");
	private static final String PROFILE = "src/test/resources/testprofile.json";
	private final static ObjectMapper jackson = new ObjectMapper();

	private ShaopParty party;
	private final TestConnection connection = new TestConnection();
	private final ProtocolRef protocol = mock(ProtocolRef.class);
	private final ProgressRounds progress = mock(ProgressRounds.class);
	private Settings settings;
	private Profile profile;
	private final Parameters parameters = new Parameters();

	@Before
	public void before() throws JsonParseException, JsonMappingException,
			IOException, URISyntaxException {
		party = new ShaopParty();
		settings = new Settings(new PartyId("party1"),
				new ProfileRef(new URI("file:" + PROFILE)), protocol, progress,
				parameters);

		String serialized = new String(Files.readAllBytes(Paths.get(PROFILE)),
				StandardCharsets.UTF_8);
		profile = jackson.readValue(serialized, Profile.class);

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
	public void testInformConnection() {
		party.connect(connection);
		// agent should not start acting just after an inform
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testInformSettings() {
		party.connect(connection);
		connection.notifyListeners(settings);
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testInformAndConnection() {
		party.connect(connection);
		party.notifyChange(settings);
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testOtherWalksAway() {
		party.connect(connection);
		party.notifyChange(settings);

		party.notifyChange(new ActionDone(new EndNegotiation(otherparty)));

		// party should not act at this point
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testAgentHasFirstTurn() {
		party.connect(connection);
		party.notifyChange(settings);

		party.notifyChange(new YourTurn());
		assertEquals(1, connection.getActions().size());
		assertTrue(connection.getActions().get(0) instanceof Offer);
	}

	@Test
	public void testAgentElicitsComparison() {
		party.connect(connection);
		party.notifyChange(settings);

		Bid bid = makeBid("3", "3"); // not yet in profile
		party.notifyChange(new ActionDone(new Offer(otherparty, bid)));
		party.notifyChange(new YourTurn());
		assertEquals(1, connection.getActions().size());
		assertTrue(connection.getActions().get(0) instanceof ElicitComparison);

	}

	@Ignore
	@Test
	public void testAgentAccepts() {
		// to make agent accept, the offered bid must score >0.9
		// we would need to mock a lot t make that happen
		// as we need to place >10 comparable bids for that.
		party.connect(connection);
		party.notifyChange(settings);

		Bid bid = makeBid("1", "1");// best pssible
		party.notifyChange(new ActionDone(new Offer(otherparty, bid)));
		party.notifyChange(new YourTurn());
		assertEquals(1, connection.getActions().size());
		assertTrue(connection.getActions().get(0) instanceof Accept);

	}

	@Test
	public void testAgentLogsFinal() {
		// this log output is optional, this is to show how to check log
		Reporter reporter = mock(Reporter.class);
		party = new ShaopParty(reporter);
		party.connect(connection);
		party.notifyChange(settings);
		party.notifyChange(new Finished(null));

		verify(reporter).log(eq(Level.INFO),
				eq("Final ourcome:Finished[null]"));
	}

	@Test
	public void testAgentsUpdatesProgress() {
		party.connect(connection);
		party.notifyChange(settings);

		party.notifyChange(new ActionDone(new Offer(null, mock(Bid.class))));

		party.notifyChange(new YourTurn());
		verify(progress).advance();
	}

	@Test
	public void testGetCapabilities() {
		assertTrue(party.getCapabilities().getBehaviours().contains(SHAOP));
	}

	private Bid makeBid(String val1, String val2) {
		Map<String, Value> map = new HashMap<>();
		map.put("iss1", new DiscreteValue(val1));
		map.put("iss2", new DiscreteValue(val2));
		return new Bid(map);

	}

}

/**
 * A "real" connection object, because the party is going to subscribe etc, and
 * without a real connection we would have to do a lot of mocks that would make
 * the test very hard to read.
 *
 */
class TestConnection extends DefaultListenable<Inform>
		implements ConnectionEnd<Inform, Action> {
	private List<Action> actions = new LinkedList<>();

	@Override
	public void send(Action action) throws IOException {
		actions.add(action);
	}

	@Override
	public Reference getReference() {
		return null;
	}

	@Override
	public URI getRemoteURI() {
		return null;
	}

	@Override
	public void close() {

	}

	@Override
	public Error getError() {
		return null;
	}

	public List<Action> getActions() {
		return actions;
	}

}
