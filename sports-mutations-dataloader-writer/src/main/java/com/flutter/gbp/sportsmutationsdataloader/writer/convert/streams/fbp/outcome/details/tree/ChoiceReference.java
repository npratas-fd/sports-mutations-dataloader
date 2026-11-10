package com.flutter.gbp.sportsmutationsdataloader.writer.convert.streams.fbp.outcome.details.tree;

/**
 * Parsed reference link from a referencing choice expression to a referenced choice number.
 */
final class ChoiceReference {

    private final int referencingChoiceNumber;
    private final int referencedChoiceNumber;

    ChoiceReference(int referencingChoiceNumber, int referencedChoiceNumber) {
        this.referencingChoiceNumber = referencingChoiceNumber;
        this.referencedChoiceNumber = referencedChoiceNumber;
    }

    int referencingChoiceNumber() {
        return referencingChoiceNumber;
    }

    int referencedChoiceNumber() {
        return referencedChoiceNumber;
    }
}
