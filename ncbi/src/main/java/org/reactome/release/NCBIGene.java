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

public class NCBIGene {
	private static final Logger logger = LogManager.getLogger();

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

	public void writeProteinFile() throws IOException {
		Path filePath = getProteinFilePath();

		Files.deleteIfExists(filePath);
		Files.createFile(filePath);

		// Write file header
		Files.write(
			filePath,
			"UniProt ID\tGene id"
				.concat(System.lineSeparator())
				.concat(System.lineSeparator())
				.getBytes()
		);

		// Append map contents
		for (NCBIEntry ncbiEntry : ncbiEntries) {
			for (String ncbiGeneId : ncbiEntry.getNcbiGeneIds()) {
				String line = ncbiEntry.getUniprotAccession() + "\t" + ncbiGeneId + System.lineSeparator();
				Files.write(filePath, line.getBytes(), StandardOpenOption.APPEND);
			}
		}
	}

	public void writeGeneXMLFiles(Session graphDBSession, int numGeneXMLFiles) throws IOException {

		Path geneErrorFilePath = getGeneErrorFilePath();
		Files.deleteIfExists(geneErrorFilePath);
		Files.createFile(geneErrorFilePath);

		int fileCount = 0;
		for (List<NCBIEntry> ncbiEntrySubList : splitList(ncbiEntries, numGeneXMLFiles)) {
			Path geneXMLFilePath = getGeneXMLFilePath(++fileCount);
			Files.deleteIfExists(geneXMLFilePath);
			Files.createFile(geneXMLFilePath);

			logger.info("Generating " + geneXMLFilePath.getFileName());
			Files.write(geneXMLFilePath, NCBIEntry.getXMLHeader().getBytes(), StandardOpenOption.APPEND);
			Files.write(
				geneXMLFilePath,
				NCBIEntry.getOpenRootTag().concat(System.lineSeparator()).getBytes(),
				StandardOpenOption.APPEND
			);
			for (NCBIEntry ncbiEntry: ncbiEntrySubList) {
				logger.info("Working on " + ncbiEntry.getUniprotAccession());
				Set<PathwayHierarchyUtilities.ReactomeEvent> topLevelPathways =
					ncbiEntry.getTopLevelPathways(graphDBSession);
				if (topLevelPathways.isEmpty()) {
					String errorMessage = ncbiEntry.getUniprotDisplayName() +
					" participates in Event(s) but no top Pathway can be found, i.e. there seem to be a pathway" +
					" which contains or is an instance of itself.\n";

					Files.write(geneErrorFilePath, errorMessage.getBytes(), StandardOpenOption.APPEND);
					continue;
				}

				for (String ncbiGeneId : ncbiEntry.getNcbiGeneIds()) {
					Files.write(
						geneXMLFilePath,
						ncbiEntry.getEntityLinkXML(ncbiGeneId, ncbiEntry.getUniprotAccession()).getBytes(),
						StandardOpenOption.APPEND
					);

					for (PathwayHierarchyUtilities.ReactomeEvent topLevelPathway : topLevelPathways) {
						Files.write(
							geneXMLFilePath,
							ncbiEntry.getEventLinkXML(ncbiGeneId, topLevelPathway).getBytes(),
							StandardOpenOption.APPEND
						);
					}
				}
				logger.info("Finished with " + ncbiEntry.getUniprotAccession());
			}

			Files.write(geneXMLFilePath, NCBIEntry.getCloseRootTag().getBytes(), StandardOpenOption.APPEND);
		}
	}

	private List<List<NCBIEntry>> splitList(List<NCBIEntry> list, int numOfSubLists) {
		int subListSize = list.size() / numOfSubLists ;
		int numberOfExtraKeys = list.size() % numOfSubLists;
		if (numberOfExtraKeys > 0) {
			subListSize += 1;
		}

		List<List<NCBIEntry>> splitLists = new ArrayList<>();

		List<NCBIEntry> subList = new ArrayList<>();
		int keyCount = 0;
		for(NCBIEntry ncbiEntry : list) {
			subList.add(ncbiEntry);
			keyCount += 1;

			// Sub map is "full" and the next sub map should be populated
			if (keyCount == subListSize) {
				splitLists.add(subList);
				subList = new ArrayList<>();
				keyCount = 0;

				if (numberOfExtraKeys > 0) {
					numberOfExtraKeys--;

					if (numberOfExtraKeys == 0) {
						subListSize--;
					}
				}
			}
		}

		return splitLists;
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
}