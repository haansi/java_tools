package ml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;


class BlastFile {
	ArrayList<String>	seqNames = new ArrayList<String>();
	Map<String, Double> bitscoreMap = new HashMap<String, Double>();
	private String queryName;
	
	
	
	public BlastFile( File file ) {
		
		try {
			BufferedReader r = new BufferedReader( new FileReader(file) );
			
			
			
			while( true ) { 
				String line = r.readLine();
			
				if( line == null ) {
					throw new RuntimeException( "eof while looking for start of bitscore section");
				}
				
				if( line.startsWith( "Query=" ) ) {
					StringTokenizer st = new StringTokenizer(line);
					st.nextToken();
					this.queryName = st.nextToken();
				} else if( line.startsWith("Sequences producing significant alignments:") ) {
					r.readLine();
					break;
				}
			}
			
			
			while( true ) {
				String line = r.readLine();

				if( line == null ) {
					throw new RuntimeException( "eof while still in bitscore section");
				}
				
				if( line.length() == 0 ) {
				//	System.out.printf( "end of score section\n" );
					break;
				}
				
				
				StringTokenizer st = new StringTokenizer(line);
				String name = st.nextToken();
				String bitscoreS = st.nextToken();
				
				double bitscore = Double.parseDouble(bitscoreS);
			
				bitscoreMap.put( name, bitscore);
				seqNames.add( name );
			}
			
			
			r.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
			throw new RuntimeException( "bailing out" );
		}
		
		
	}
	
	ArrayList<String>getSeqNames() {
		return seqNames;
	}
	
	double getBitscore( String seqName ) {
		Double s = bitscoreMap.get(seqName);
		
		if( s != null ) {
			return s.doubleValue();
		} else {
			throw new RuntimeException( "seqName not in blastfile: '" + seqName + "'" );
			//return s.POSITIVE_INFINITY; 
		}
		
	}
	
	String getQueryName() {
		return queryName;
	}
	
}

public class BlastOutTreedist {

	public static void main(String[] args) {
		
		boolean automode = args[0].equals("--auto"); 
		
        File reftreeFile = new File( args[1] );
        LN reftree;
        {
            TreeParser tpreftree = new TreeParser(reftreeFile);
            reftree = tpreftree.parse();
        }

        // highest path weight in reference tree (=path with the highest sum of edge weights, no necessarily the longest path)
        double reftreeDiameter = ClassifierLTree.treeDiameter(reftree);

        
        if( !automode ) {
	        
	        File blastFile = new File( args[0] );
			BlastFile bf = new BlastFile(blastFile);
			
			// parse reference tree used for weighted branch difference stuff
	        		
	        //Map<String,String[]> splitmap = ClassifierLTree.parseSplits( rnFile );
	        
	        String queryName = bf.getQueryName();
	        if( queryName == null ) {
	        	throw new RuntimeException( "BlastFile has not query seq" );
	        }
	        
	        if( false ) {
				for( String name : bf.getSeqNames() ) {
					//System.out.printf( "%s => %f\n", name, bf.getBitscore(name));
					
					double distUW = getPathLenTipToTip( reftree, queryName, name, true );
					double dist = getPathLenTipToTip( reftree, queryName, name, false );
					System.out.printf( "%s\t%f\t%d\t%f\t%f\n", name, bf.getBitscore(name), (int)distUW, dist, dist / reftreeDiameter );
				}
	        } else {
	        	if( !queryName.equals( bf.getSeqNames().get(0))) {
	        		throw new RuntimeException( "ooops. query sequence is not the best blast hit. bailing out");
	        	}
	        	
	        	String name = bf.getSeqNames().get(1);
	        	double distUW = getPathLenTipToTip( reftree, queryName, name, true );
				double dist = getPathLenTipToTip( reftree, queryName, name, false );
				System.out.printf( "%s\t%f\t%d\t%f\t%f\n", name, bf.getBitscore(name), (int)distUW, dist, dist / reftreeDiameter );
	        	
	        }
        } else {
        	final String autoprefix;
        	if( args.length > 2 ) {
        		autoprefix = args[2];
        	} else {
        		autoprefix = "";
        	}
        	
        	File cwd = new File(".");
        	
        	String[] files = cwd.list( new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith(autoprefix) && name.endsWith(".fa");
				}
			});
        	
        	Arrays.sort(files);
        	
        	for( String blastFile : files ) {
//        		System.out.printf( "%s:\n", blastFile );
    			BlastFile bf = new BlastFile(new File(blastFile));
    			
    			// parse reference tree used for weighted branch difference stuff
    	        		
    	        //Map<String,String[]> splitmap = ClassifierLTree.parseSplits( rnFile );
    	        
    	        String queryName = bf.getQueryName();
    	        if( queryName == null ) {
    	        	throw new RuntimeException( "BlastFile has not query seq" );
    	        }
    	        

    	        
    	        ArrayList<String> seqNames = bf.getSeqNames();
    	        
    	        int hit = -1;
    	        
    	        for( int i = 0; i < seqNames.size(); i++ ) {
    	        	if( !seqNames.get(i).equals(queryName)) {
    	        		hit = i;
    	        		break;
    	        	}
    	        }
    	        
    	        if( hit < 0 ) {
    	        	throw new RuntimeException( "could not find first blast hit!?");
    	        }
    	        
    	        
//	        	if( !queryName.equals( bf.getSeqNames().get(0))) {
//	        		throw new RuntimeException( "ooops. query sequence is not the best blast hit. bailing out");
//	        	}

	        	String name = bf.getSeqNames().get(hit);
	        	double distUW = getPathLenTipToTip( reftree, queryName, name, true );
				double dist = getPathLenTipToTip( reftree, queryName, name, false );
				
				// ugly: extract seq and gap from the blast file name
				int idx1stUnderscore = blastFile.indexOf('_');
				int idx2ndUnderscore = blastFile.indexOf('_', idx1stUnderscore + 1);
				int idxDot = blastFile.lastIndexOf('.');
				
				String seq = blastFile.substring(idx1stUnderscore+1, idx2ndUnderscore);
				String gap = blastFile.substring(idx2ndUnderscore+1, idxDot);
				
				System.out.printf( "%s\t%s\t%s\t%s\t%d\t%f\t%f\n", seq, gap, queryName, name, (int)distUW, dist, dist / reftreeDiameter );
    	        	
    	                		
        		
        		
        		
        	}
        	
        }
	}

	private static double getPathLenTipToTip(LN tree, String startName, String endName, boolean unweighted) {
		LN[] list = LN.getAsList(tree);
		
		LN start = null;
		LN end = null;
		
		for( LN n : list ) {
			if( n.data.isTip ) {
				String tipName = n.data.getTipName();
				
				if( start == null && tipName.equals(startName) ) {
					start = n;
				} 
				if( end == null && tipName.equals(endName) ) {
					end = n;
				}
			}
			
			if( start != null && end != null ) {
				break;
			}
		}

		if( start == null ) {
			throw new RuntimeException( "could not find node for start tip: " + startName );
		}
		if( end == null ) {
			throw new RuntimeException( "could not find node for end tip: " + startName );
		}
		
		if( start == end ) {
			return 0.0;
		}
		
		return getPathLenNodeToTipNoBack(start.back, end, unweighted) + (unweighted ? 1.0 : start.backLen);
		
	}

	private static double getPathLenNodeToTipNoBack(LN start, LN end, boolean unweighted) {
		if( start == end ) {
			return 0.0;
		} else if( start.data.isTip ) {
			return Double.POSITIVE_INFINITY;
		} else {
			{
				double len = getPathLenNodeToTipNoBack(start.next.back, end, unweighted);
				if( len < Double.POSITIVE_INFINITY ) {
					return len + (unweighted ? 1.0 : start.next.backLen);
				}
			}
			{
				double len = getPathLenNodeToTipNoBack(start.next.next.back, end, unweighted);
				if( len < Double.POSITIVE_INFINITY ) {
					return len + (unweighted ? 1.0 : start.next.next.backLen);
				}
			}
			
			return Double.POSITIVE_INFINITY;
		}
		
	}
}
