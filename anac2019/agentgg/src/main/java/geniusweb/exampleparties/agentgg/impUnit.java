package geniusweb.exampleparties.agentgg;

import java.util.Comparator;

import geniusweb.issuevalue.Value;

/**
 * importance unit . Contains importance of a {@link Value} of some issue. The
 * values in this class are hard referenced and changed by the {@link ImpMap}.
 */
public class impUnit {
	public Value valueOfIssue;
	public int weightSum = 0;
	public int count = 0;
	public double meanWeightSum = 0.0f; // counts #occurences of this value.

	public impUnit(Value value) {
		this.valueOfIssue = value;
	}

	public String toString() {
		return String.format("%s %f", valueOfIssue, meanWeightSum);
	}

	// Overriding the comparator interface
	static class meanWeightSumComparator implements Comparator<impUnit> {
		public int compare(impUnit o1, impUnit o2) {
			if (o1.meanWeightSum < o2.meanWeightSum) {
				return 1;
			} else if (o1.meanWeightSum > o2.meanWeightSum) {
				return -1;
			}
			return 0;
		}
	}

}
