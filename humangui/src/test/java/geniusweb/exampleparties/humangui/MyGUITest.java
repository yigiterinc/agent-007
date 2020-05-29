package geniusweb.exampleparties.humangui;

import static org.mockito.Mockito.mock;

import java.awt.BorderLayout;
import java.awt.Container;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JFrame;
import javax.websocket.DeploymentException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import geniusweb.actions.Action;
import geniusweb.actions.PartyId;
import geniusweb.connection.ConnectionEnd;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.ProgressRounds;
import geniusweb.references.Parameters;
import geniusweb.references.ProfileRef;
import geniusweb.references.ProtocolRef;
import tudelft.utilities.logging.Reporter;

public class MyGUITest {
	private final ProtocolRef protocol = mock(ProtocolRef.class);
	private final ProgressRounds progress = mock(ProgressRounds.class);
	private static final String PROFILE = "src/test/resources/testprofile.json";
	private final Parameters parameters = new Parameters();
	private Reporter reporter = mock(Reporter.class);
	private MyGUI gui;
	private JFrame frame;
	private BiddingInfo info;

	@Before
	public void before()
			throws IOException, DeploymentException, URISyntaxException {
		Settings settings = new Settings(new PartyId("party1"),
				new ProfileRef(new URI("file:" + PROFILE)), protocol, progress,
				parameters);

		ProfileInterface profileint = ProfileConnectionFactory
				.create(settings.getProfile().getURI(), reporter);
		ConnectionEnd<Inform, Action> conn = mock(ConnectionEnd.class);
		info = new BiddingInfo(settings, conn, reporter, profileint);
		gui = new MyGUI(info);

		// not strictly necesary but useful to visually check the process
		frame = new JFrame();
		Container content = frame.getContentPane();
		content.setLayout(new BorderLayout());
		content.add(gui, BorderLayout.CENTER);
		frame.pack();
		frame.setVisible(true);

	}

	@After
	public void after() {
		frame.setVisible(false);
	}

	@Test
	public void smokeTest() throws IOException, DeploymentException,
			URISyntaxException, InterruptedException {
		Thread.sleep(2000);
	}

}
