package geniusweb.exampleparties.agentgg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.PartialOrdering;

/**
 * Importance map. One is created for each party. The key (String) is the issue
 * and value is list of Importances of the values.
 *
 */
@SuppressWarnings("serial")
public class ImpMap extends HashMap<String, List<impUnit>> {
	private Domain domain;

	// importance map
	public ImpMap(PartialOrdering profile) {
		super();
		this.domain = profile.getDomain();
		// Create empty my import map and opponent's value map
		for (String issue : domain.getIssues()) {
			ValueSet values = domain.getValues(issue);
			List<impUnit> issueImpUnit = new ArrayList<>();
			for (Value value : values) {
				issueImpUnit.add(new impUnit(value));
			}
			this.put(issue, issueImpUnit);
		}
	}

	/**
	 * Update opponent map. Increases the meanWeightSum of the values of this
	 * bid.
	 * 
	 * @param receivedOfferBid tbe received opponent bid.
	 */
	public void opponent_update(Bid receivedOfferBid) {
		for (String issue : receivedOfferBid.getIssues()) {
			ValueSet values = domain.getValues(issue);
			List<impUnit> currentIssueList = this.get(issue);
			for (impUnit currentUnit : currentIssueList) {
				if (currentUnit.valueOfIssue
						.equals(receivedOfferBid.getValue(issue))) {
					currentUnit.meanWeightSum += 1;
					break;
				}
			}
		}
		for (List<impUnit> impUnitList : this.values()) {
			impUnitList.sort(new impUnit.meanWeightSumComparator());
		}
	}

	/**
	 * Update your own importance map Traverse the known bidOrder and update the
	 * "weight sum" and "number of times" in the import table.
	 * 
	 * @param bids a list of ordered bids, worst bid first, best bid last
	 */
	public void self_update(List<Bid> bidOrdering) {
		int currentWeight = 0;
		for (Bid bid : bidOrdering) {
			currentWeight += 1;
			for (String issue : bid.getIssues()) {
				List<impUnit> currentIssueList = this.get(issue);
				for (impUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.toString()
							.equals(bid.getValue(issue).toString())) {
						currentUnit.weightSum += currentWeight;
						currentUnit.count += 1;
						break;
					}
				}
			}
		}
		// Calculate weights
		for (List<impUnit> impUnitList : this.values()) {
			for (impUnit currentUnit : impUnitList) {
				if (currentUnit.count == 0) {
					currentUnit.meanWeightSum = 0.0;
				} else {
					currentUnit.meanWeightSum = (double) currentUnit.weightSum
							/ (double) currentUnit.count;
				}
			}
		}
		// Sort
		for (List<impUnit> impUnitList : this.values()) {
			impUnitList.sort(new impUnit.meanWeightSumComparator());
		}
		// Find the minimum
		double minMeanWeightSum = Double.POSITIVE_INFINITY;
		for (Map.Entry<String, List<impUnit>> entry : this.entrySet()) {
			double tempMeanWeightSum = entry.getValue()
					.get(entry.getValue().size() - 1).meanWeightSum;
			if (tempMeanWeightSum < minMeanWeightSum) {
				minMeanWeightSum = tempMeanWeightSum;
			}
		}
		// Minus all values
		for (List<impUnit> impUnitList : this.values()) {
			for (impUnit currentUnit : impUnitList) {
				currentUnit.meanWeightSum -= minMeanWeightSum;
			}
		}
	}

	/**
	 * @param bid the bid to get the importance (utility?) of.
	 * @return the importance value of bid. CHECK is this inside [0,1]?
	 */
	public double getImportance(Bid bid) {
		double bidImportance = 0.0;
		for (String issue : bid.getIssues()) {
			Value value = bid.getValue(issue);
			double valueImportance = 0.0;
			for (impUnit i : this.get(issue)) {
				if (i.valueOfIssue.equals(value)) {
					valueImportance = i.meanWeightSum;
					break;
				}
			}
			bidImportance += valueImportance;
		}
		return bidImportance;
	}
}
