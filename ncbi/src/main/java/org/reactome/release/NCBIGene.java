package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.reactome.release.Utilities.appendWithNewLine;

/**
 * File generator for NCBI Gene.  This class has logic for producing a file for
 * NCBI Gene XML, describing the relationship between NCBI Gene identifiers and
 * Reactome UniProt entries as well as their top level pathways.
 * It also can create a file for NCBI Protein, describing the relationship between
 * NCBI Gene identifiers and Reactome UniProt entries in a simple tab delimited format.
 * @author jweiser
 */
public class NCBIGene {
	private static final Logger logger = LogManager.getLogger();
	private static final Logger ncbiGeneLogger = LogManager.getLogger("ncbiGeneLog");

	private static final String rootTag = "LinkSet";

	private List<NCBIEntry> ncbiEntries;
	private String outputDir;
	private int reactomeVersion;

	public static NCBIGene getInstance(List<NCBIEntry> ncbiEntries, String outputDir, int reactomeVersion) {
		return new NCBIGene(ncbiEntries, outputDir, reactomeVersion);
	}

	private NCBIGene(List<NCBIEntry> ncbiEntries, String outputDir, int reactomeVersion) {
		this.ncbiEntries = ncbiEntries;
		this.outputDir = outputDir;
		this.reactomeVersion = reactomeVersion;
	}

	/**
	 * Writes NCBI Protein tab-delimited file describing the UniProt to NCBI Gene identifier relationships in
	 * Reactome to a pre-set output directory
	 * @throws IOException Thrown if creating or appending for file fails
	 */
	public void writeProteinFile() throws IOException {
		Path filePath = getProteinFilePath();

		Files.deleteIfExists(filePath);
		Files.createFile(filePath);

		logger.info("Writing proteins_version file");

		// Write file header
		Files.write(
			filePath,
			"UniProt ID\tGene id"
				.concat(System.lineSeparator())
				.concat(System.lineSeparator())
				.getBytes()
		);

		// Append map contents
		Set<String> proteinLines = new LinkedHashSet<>();
		for (NCBIEntry ncbiEntry : ncbiEntries) {
			for (String ncbiGeneId : ncbiEntry.getNcbiGeneIds()) {
				proteinLines.add(ncbiEntry.getUniprotAccession() + "\t" + ncbiGeneId);
			}
		}

		for (String line : proteinLines) {
			appendWithNewLine(line, filePath);
		}

		logger.info("Finished writing proteins_version file");
	}

	/**
	 * Writes NCBI Gene XML files describing the relationships between NCBI Gene identifiers and UniProt entries as
	 * well as their Reactome pathways to pre-set output directory
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @param numGeneXMLFiles Number of files to divide the Gene XML entries among
	 * @throws IOException Thrown if creating or appending for any file fails
	 */
	public void writeGeneXMLFiles(Session graphDBSession, int numGeneXMLFiles) throws IOException {
		Path geneErrorFilePath = getGeneErrorFilePath();
		Files.deleteIfExists(geneErrorFilePath);
		Files.createFile(geneErrorFilePath);

		logger.info("Writing gene XML file(s)");

		Set<String> ncbiGeneXMLNodeStrings = new LinkedHashSet<>();
		for (NCBIEntry ncbiEntry : ncbiEntries) {
			ncbiGeneLogger.info("Working on " + ncbiEntry.getUniprotAccession());

			Set<ReactomeEvent> topLevelPathways = ncbiEntry.getTopLevelPathways(graphDBSession);
			if (topLevelPathways.isEmpty()) {
				String errorMessage = ncbiEntry.getUniprotDisplayName() +
									  " participates in Event(s) but no top Pathway can be found, i.e. there seem to be a pathway" +
									  " which contains or is an instance of itself.\n";

				Files.write(geneErrorFilePath, errorMessage.getBytes(), StandardOpenOption.APPEND);
				continue;
			}

			for (String ncbiGeneId : ncbiEntry.getNcbiGeneIds()) {
				ncbiGeneXMLNodeStrings.add(ncbiEntry.getEntityLinkXML(ncbiGeneId));

				for (ReactomeEvent topLevelPathway : topLevelPathways) {
					ncbiGeneXMLNodeStrings.add(ncbiEntry.getEventLinkXML(ncbiGeneId, topLevelPathway));
				}
			}

			ncbiGeneLogger.info("Finished with " + ncbiEntry.getUniprotAccession());
		}

		int fileCount = 0;
		for (Set<String> ncbiGeneXMLNodeStringsSubSet : Utilities.splitSet(ncbiGeneXMLNodeStrings, numGeneXMLFiles)) {
			Path geneXMLFilePath = getGeneXMLFilePath(++fileCount);
			Files.deleteIfExists(geneXMLFilePath);
			Files.createFile(geneXMLFilePath);

			logger.info("Generating " + geneXMLFilePath.getFileName());

			appendWithNewLine(getXMLHeader(), geneXMLFilePath);
			appendWithNewLine(getOpenRootTag(), geneXMLFilePath);
			for (String ncbiGeneXMLNodeString : ncbiGeneXMLNodeStringsSubSet) {
				appendWithNewLine(ncbiGeneXMLNodeString, geneXMLFilePath);
			}

			appendWithNewLine(getCloseRootTag(), geneXMLFilePath);
		}

		logger.info("Finished writing gene XML file(s)");
	}

	private Path getProteinFilePath() {
		return Paths.get(outputDir, "proteins_version" + reactomeVersion);
	}

	private Path getGeneXMLFilePath(int fileCount) {
		String fileName = "gene_reactome" + reactomeVersion + "-" + fileCount + ".xml";
		return Paths.get(outputDir, fileName);
	}

	private Path getGeneErrorFilePath() {
		return Paths.get(outputDir, "geneentrez_" + reactomeVersion + ".err");
	}

	/**
	 * Returns the XML header for the NCBI Gene file containing the Reactome entity and event base URLs
	 * @return XML header as String
	 */
	public static String getXMLHeader() {
		return String.join(System.lineSeparator(),
						   "<?xml version=\"1.0\"?>",
						   "<!DOCTYPE LinkSet PUBLIC \"-//NLM//DTD LinkOut 1.0//EN\"",
						   "\"http://www.ncbi.nlm.nih.gov/entrez/linkout/doc/LinkOut.dtd\"",
						   "[",
						   "<!ENTITY entity.base.url \"" + ReactomeConstants.UNIPROT_QUERY_URL + "\">",
						   "<!ENTITY event.base.url \"" + ReactomeConstants.PATHWAY_BROWSER_URL + "\">",
						   "]>"
		).concat(System.lineSeparator());
	}

	/**
	 * Returns the opening XML root level tag for the NCBI Gene file
	 * @return XML opening root level tag as String
	 */
	public static String getOpenRootTag() {
		return "<" + rootTag + ">";
	}

	/**
	 * Returns the closing XML root level tag for the NCBI Gene file
	 * @return XML closing root level tag as String
	 */
	public static String getCloseRootTag() {
		return "</" + rootTag + ">";
	}
}