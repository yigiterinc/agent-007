package geniusweb.exampleparties.humangui;

import java.awt.BorderLayout;
import java.util.LinkedList;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;

import geniusweb.actions.Accept;
import geniusweb.actions.EndNegotiation;
import geniusweb.actions.Offer;
import geniusweb.issuevalue.Value;
import geniusweb.profile.Profile;

/**
 * the human GUI panel
 *
 */
@SuppressWarnings("serial")
public class MyGUI extends JPanel {

	// not static because buttons are mutable (have listeners)
	private final JButton acceptButton = new JButton("Accept");
	private final JButton offerButton = new JButton("Place Offer");
	private final JButton stopButton = new JButton("Stop Negotiation");
	private final BiddingInfo info;

	public MyGUI(BiddingInfo biddinginfo) {
		if (biddinginfo == null)
			throw new NullPointerException("biddinginfo=null");
		this.info = biddinginfo;
		setLayout(new BorderLayout());
		JTable table = new JTable(new BiddingTableModel(info)) {
			@Override
			public TableCellEditor getCellEditor(int row, int col) {
				if (row < 0 || row > info.getIssues().size())
					return null;
				String issue = info.getIssues().get(row);
				LinkedList<Value> values = new LinkedList<Value>();
				info.getProfile().getDomain().getValues(issue)
						.forEach(v -> values.add(v));
				return new DefaultCellEditor(new JComboBox<>(values.toArray()));
			}
		};
		JScrollPane tablecontainer = new JScrollPane(table);
		add(tablecontainer, BorderLayout.CENTER);

		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
		buttonPane.add(Box.createHorizontalGlue());
		buttonPane.add(offerButton);
		buttonPane.add(acceptButton);
		buttonPane.add(stopButton);
		add(buttonPane, BorderLayout.SOUTH);

		biddinginfo.addListener(data -> updateGui(data));
		acceptButton.addActionListener(e -> accept());
		offerButton.addActionListener(e -> offer());
		stopButton.addActionListener(e -> stop());
		updateGui(false);
	}

	private void offer() {
		info.doAction(new Offer(info.getMyId(), info.getCurrentBid()));
	}

	private void accept() {
		info.doAction(
				new Accept(info.getMyId(), info.getReceivedOffer().getBid()));
	}

	private void stop() {
		info.doAction(new EndNegotiation(info.getMyId()));
	}

	/**
	 * Update visibility of the buttons when changes occur in model
	 */
	private void updateGui(Object change) {
		if (change instanceof Boolean) {
			// boolean contains isMyTurn
			Boolean isEnabled = (Boolean) change;
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					offerButton.setEnabled(isEnabled);
					stopButton.setEnabled(isEnabled);
					acceptButton.setEnabled(
							isEnabled && info.getReceivedOffer() != null);
				}
			});
		} else if (change instanceof Profile) {
			JOptionPane.showMessageDialog(this,
					"Notice, your preference profile has been changed!");
		}
	}

}
