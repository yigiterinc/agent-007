package geniusweb.exampleparties.humangui;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import geniusweb.actions.Offer;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Value;

/**
 * An adapter that moulds the BiddingInfo into a {@link TableModel}. Makes a
 * 3-col table: left column the issues, middle col the last received offer, and
 * right col the offer the user is preparing. The right column can be edited.
 */
public class BiddingTableModel implements TableModel {
	private final static String[] cols = { "Issue", "Last received offer",
			"Your next offer" };
	private BiddingInfo info;
	private final List<TableModelListener> listeners = new LinkedList<>();

	public BiddingTableModel(BiddingInfo info) {
		this.info = info;
		info.addListener(data -> update(data));
	}

	@Override
	public int getRowCount() {
		return info.getIssues().size();
	}

	@Override
	public int getColumnCount() {
		return cols.length;
	}

	@Override
	public String getColumnName(int columnIndex) {
		return cols[columnIndex];
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case 0:
			return String.class;
		case 1:
		case 2:
			return Value.class;
		}
		return null;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return columnIndex == 2;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		String issue = getIssue(rowIndex);
		if (issue == null)
			return "";
		switch (columnIndex) {
		case 0:
			return issue;
		case 1:
			if (info.getReceivedOffer() == null)
				return null;
			return info.getReceivedOffer().getBid().getValue(issue);
		case 2:
			return info.getCurrentBid().getValue(issue);
		}
		return null;
	}

	@Override
	public void setValueAt(Object val, int rowIndex, int columnIndex) {
		if (!(val instanceof Value))
			throw new IllegalArgumentException("New val must be a value");
		String issue = getIssue(rowIndex);
		if (issue == null)
			return;

		Map<String, Value> newvalues = new HashMap<>();
		newvalues.putAll(info.getCurrentBid().getIssueValues());
		newvalues.put(issue, (Value) val);
		info.setCurrentBid(new Bid(newvalues));
	}

	@Override
	public void addTableModelListener(TableModelListener l) {
		listeners.add(l);
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		listeners.remove(l);
	}

	/************************ PRIVATE ************************/
	private void update(Object data) {
		if (data instanceof Offer) {
			notifyTableListeners();
		}
	}

	/**
	 * @param rowIndex
	 * @return Get issue associated with given row, or null if no such row.
	 */
	private String getIssue(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= info.getIssues().size())
			return null;
		return info.getIssues().get(rowIndex);

	}

	private void notifyTableListeners() {
		for (TableModelListener l : listeners) {
			l.tableChanged(new TableModelEvent(this));
		}
	}

}
