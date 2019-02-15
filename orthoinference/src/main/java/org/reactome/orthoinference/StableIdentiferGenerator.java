package org.reactome.orthoinference;

import org.gk.model.GKInstance;

import java.util.HashMap;
import java.util.Map;

/*
*  All PhysicalEntitys, ReactionlikeEvents and Pathways are routed to this class to generate their stable identifiers
*/
public class StableIdentiferGenerator {

    private static String speciesAbbreviation = null;
    public static void generateOrthologousStableId(GKInstance infReactionInst) {


    }

    public static void setSpeciesAbbreviation(String speciesAbbreviationCopy) {
        speciesAbbreviation = speciesAbbreviationCopy;
    }
}
