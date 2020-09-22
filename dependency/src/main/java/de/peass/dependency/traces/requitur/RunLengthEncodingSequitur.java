package de.peass.dependency.traces.requitur;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.peass.dependency.traces.requitur.content.Content;
import de.peass.dependency.traces.requitur.content.RuleContent;

public class RunLengthEncodingSequitur {

	private static final Logger LOG = LogManager.getLogger(RunLengthEncodingSequitur.class);

	private final Sequitur sequitur;

	public RunLengthEncodingSequitur(final Sequitur sequitur) {
		this.sequitur = sequitur;
	}

	public void reduce() {
		reduce(sequitur.getStartSymbol());
	}

	private void reduce(final Symbol start) {
		Symbol iterator = start.getSuccessor();
		subReduce(iterator);
		while (iterator != null && iterator.getValue() != null && iterator.getSuccessor() != null && iterator.getSuccessor().getValue() != null) {
			final Symbol successor = iterator.getSuccessor();
			subReduce(successor);
			if (iterator.valueEqual(successor)) {
				if (successor.getSuccessor() != null) {
					iterator.setSuccessor(successor.getSuccessor());
					successor.getSuccessor().setPredecessor(iterator);
				} else {
					iterator.setSuccessor(null);
				}
				iterator.setOccurences(iterator.getOccurences() + successor.getOccurences());
			} else {
				iterator = iterator.getSuccessor();
			}
		}
	}

	private void subReduce(final Symbol containingSymbol) {
		if (containingSymbol.isRule()) {
			LOG.trace("Reduce: {}", containingSymbol);
			final Rule rule = containingSymbol.getRule();
			final Symbol iterator = rule.getAnchor();
			reduce(iterator);
			final Symbol firstSymbolOfRule = iterator.getSuccessor();
			LOG.trace("Reduced: {}", rule.getName());
			LOG.trace("Rule-Length: {}", rule.getElements().size() + " " + (firstSymbolOfRule.getSuccessor() == iterator));
			if (firstSymbolOfRule.getSuccessor() == iterator) { // Irgendwie entsteht hier die Zuordnung #1 auf Regel #0
				containingSymbol.setValue(firstSymbolOfRule.getValue());
				containingSymbol.setOccurences(containingSymbol.getOccurences() * firstSymbolOfRule.getOccurences());
				containingSymbol.decrementUsage(rule);
				if (firstSymbolOfRule.getRule() != null) {
					containingSymbol.setRule(firstSymbolOfRule.getRule());
				} else {
					firstSymbolOfRule.setRule(null);
				}

			}
			// TraceStateTester.testTrace(sequitur);
		}
	}

	public List<ReducedTraceElement> getReadableRLETrace() {
		Symbol iterator = sequitur.getStartSymbol().getSuccessor();
		final List<ReducedTraceElement> trace = new LinkedList<>();
		while (iterator != null) {
			addReadableElement(iterator, trace);
			iterator = iterator.getSuccessor();
		}
		return trace;
	}

	private int addReadableElement(final Symbol iterator, final List<ReducedTraceElement> trace) {
		final Content content = iterator.getValue();
		LOG.trace("Add: {} {}", content, content.getClass());
		final ReducedTraceElement newElement = new ReducedTraceElement(content, iterator.getOccurences());
		if (content instanceof RuleContent) {
			final RuleContent currentContent = (RuleContent) content;
			trace.add(newElement);
			final Symbol anchor = iterator.getRule().getAnchor();
			Symbol ruleIterator = anchor.getSuccessor();
			int subelements = 1;
			while (ruleIterator != anchor) {
				subelements += addReadableElement(ruleIterator, trace);
				ruleIterator = ruleIterator.getSuccessor();
			}
			currentContent.setCount(subelements - 1);
			return subelements;
		} else {
			trace.add(newElement);
			return 1;
		}
	}

}
