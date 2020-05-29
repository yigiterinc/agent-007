package geniusweb.exampleparties.humangui;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import geniusweb.actions.Action;
import geniusweb.connection.ConnectionEnd;
import geniusweb.party.inform.Inform;
import geniusweb.references.Reference;
import tudelft.utilities.listener.DefaultListenable;

/**
 * A "real" connection object, because the party is going to subscribe etc, and
 * without a real connection we would have to do a lot of mocks that would make
 * the test very hard to read.
 *
 */
public class TestConnection extends DefaultListenable<Inform>
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
