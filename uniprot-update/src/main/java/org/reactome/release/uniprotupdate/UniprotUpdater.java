package org.reactome.release.uniprotupdate;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceEdit;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.release.uniprotupdate.dataschema.GeneName;
import org.reactome.release.uniprotupdate.dataschema.Name;
import org.reactome.release.uniprotupdate.dataschema.UniprotData;

/**
 * 
 * @author sshorser
 *
 */
public class UniprotUpdater
{

	private static final String HOMO_SAPIENS = "Homo sapiens";
	// List of species names was taken from uniprot_xml2sql_isoform.pl:84
	private static final Set<String> speciesToUpdate = new HashSet<String>( Arrays.asList(HOMO_SAPIENS, "Mus musculus", "Rattus norvegicus",
																			"Bos taurus", "Gallus gallus", "Drosophila melanogaster",
																			"Caenorhabditis elegans", "Saccharomyces cerevisiae", "Schizosaccharomyces pombe",
																			"Human immunodeficiency virus type 1", "Human immunodeficiency virus type 2", "Influenza A virus") );
	
	private static String geneNamesListToString(List<GeneName> geneNames)
	{
		StringBuilder sb = new StringBuilder();
		for (GeneName geneName : geneNames)
		{
			for (Name name : geneName.getNames())
			{
				sb.append("\"").append(name.getValue()).append("\", ");
			}
			
		}
		return sb.toString();
	}
	
	/**
	 * Updates UniProt instances.
	 * @param uniprotData - The uniprot data that was extracted from the XML file. This will be a list of UniprotData objects, each object representing an &lt;entry/&gt; entity from the file.
	 * @param referenceDNASequences - A map of ReferenceDNASequence objects, keyed by their Identifier (ReferenceDNASequences without an identifier should not be in this list).
	 * @throws Exception 
	 */
	public void updateUniprotInstances(MySQLAdaptor adaptor, List<UniprotData> uniprotData, Map<String, GKInstance> referenceDNASequences, InstanceEdit instanceEdit) throws Exception
	{
		@SuppressWarnings("unchecked")
		GKInstance humanSpecies = ((List<GKInstance>) adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.species, ReactomeJavaConstants.name, "=", HOMO_SAPIENS)).get(0);
		
		for (UniprotData data : uniprotData)
		{
			// first, let's make sure this piece of data is for a species that we can update via Uniprot Update.
			if (speciesToUpdate.contains(data.getScientificName()))
			{
				// for human data, we may need to update a ReferenceDNASequence.
				if (data.getScientificName().equals(HOMO_SAPIENS))
				{
					// Report when there are multiple gene names.
					if (data.getGeneNames().size() > 0)
					{
						System.out.println("Accession " + data.getAccessions().toString() + "multiple gene names: " + geneNamesListToString(data.getGeneNames()));
					}
					for (GeneName geneName : data.getGeneNames())
					{
						for (Name name : geneName.getNames())
						{
							boolean modified = false;
							// Check to see if the ENSEMBL ID (Remember: the XSL only selects for "Ensembl" gene names) is in the list of ReferenceDNASequences.
							String geneNameFromFile = name.getValue();
							if (referenceDNASequences.containsKey(geneNameFromFile))
							{
								
								// If this instance already exists in the database, let's update it.
								GKInstance referenceDNASequence = referenceDNASequences.get(geneNameFromFile);
								GKInstance speciesFromDB = (GKInstance) referenceDNASequence.getAttributeValue(ReactomeJavaConstants.Species);
								Set<String> speciesNamesFromDB = (Set<String>) speciesFromDB.getAttributeValuesList(ReactomeJavaConstants.name);
								if (!speciesNamesFromDB.contains(data.getScientificName()))
								{
									referenceDNASequence.setAttributeValue(ReactomeJavaConstants.species, humanSpecies);
								}
								
								Set<String> geneNamesFromDB = (Set<String>)referenceDNASequence.getAttributeValuesList(ReactomeJavaConstants.geneName);
								if (!geneNamesFromDB.contains(geneNameFromFile))
								{
									modified = true;
									referenceDNASequence.addAttributeValue(ReactomeJavaConstants.geneName, geneNameFromFile);
								}
								
								if (modified)
								{
									referenceDNASequence.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
									adaptor.updateInstanceAttribute(referenceDNASequence, ReactomeJavaConstants.geneName);
									adaptor.updateInstanceAttribute(referenceDNASequence, ReactomeJavaConstants.modified);
								}
							}
						}
					}
				}
				// Process the rest of the data - chains, isoforms...
			}
		}
	}
	
}