package tsm;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import static java.lang.Character.isDigit;
import static java.lang.Character.isLetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * A collection of WordVec instances for each unique term in
 * the collection.
 * @author Debasis
 */
public class WordVecs {

    Properties prop;
    int k;
    HashMap<String, WordVec> wordvecmap;
    HashMap<String, List<WordVec>> nearestWordVecsMap; // Store the pre-computed NNs after read from file
    static WordVecs singleTon;

    //ArrayList<WordVec> distList;
    
    public WordVecs(String propFile) throws Exception {
        
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        //distList = new ArrayList<>(wordvecmap.size());
        k = Integer.parseInt(prop.getProperty("wordvecs.numnearest", "20"));        
        String wordvecFile = prop.getProperty("wordvecs.vecfile");

        wordvecmap = new HashMap();
        try (FileReader fr = new FileReader(wordvecFile);
            BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                WordVec wv = new WordVec(line);
                if(isLegalToken(wv.word))
                    wordvecmap.put(wv.word, wv);
            }
        }
    }

    public WordVecs(Properties prop) throws Exception { 
        this.prop = prop;
        //distList = new ArrayList<>(wordvecmap.size());
        k = Integer.parseInt(prop.getProperty("wordvecs.numnearest", "25"));        
        String wordvecFile = prop.getProperty("wordvecs.vecfile");
        
        if (wordvecFile == null)
            return;
        
        wordvecmap = new HashMap();
        try (FileReader fr = new FileReader(wordvecFile);
            BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                WordVec wv = new WordVec(line);
                if(isLegalToken(wv.word))
                    wordvecmap.put(wv.word, wv);
            }
        }
    }
    
    static public WordVecs createInstance(Properties prop) throws Exception {
        if(singleTon == null) {
            singleTon = new WordVecs(prop);
            singleTon.loadPrecomputedNNs();
            System.out.println("Precomputed NNs loaded");
        }
        return singleTon;
    }
    
    public void computeAndStoreNNs() throws FileNotFoundException {
        String NNDumpPath = prop.getProperty("NNDumpPath");
        if(NNDumpPath!=null) {
            File f = new File(NNDumpPath);
        }
        else {
            System.out.println("Null found");
            return;
        }

        System.out.println("Dumping the NN in: "+ NNDumpPath);
        PrintWriter pout = new PrintWriter(NNDumpPath);

        System.out.println("Precomputing NNs for each word");

        for (Map.Entry<String, WordVec> entry : wordvecmap.entrySet()) {
            WordVec wv = entry.getValue();
            if (isLegalToken(wv.word)) { 
                System.out.println("Precomputing NNs for " + wv.word);
                List<WordVec> nns = getNearestNeighbors(wv.word);
                if (nns != null) {
                    pout.print(wv.word + "\t");
                    for (int i = 0; i < nns.size(); i++) {
                        WordVec nn = nns.get(i);
                        pout.print(nn.word + ":" + nn.querySim + "\t");
                    }
                    pout.print("\n");
                }
            }
        }
        pout.close();
    }
    
    public List<WordVec> getPrecomputedNNs(String queryWord) {
        return nearestWordVecsMap.get(queryWord);
    }

    public List<WordVec> getPrecomputedNNs(String queryWord, int k, float thresh) {
        List<WordVec> nnlist = nearestWordVecsMap.get(queryWord);
        if (nnlist == null)
            return null; 
        int kprime = Math.min(k, nnlist.size());
        List<WordVec> sublist = nnlist.subList(0, kprime);
        List<WordVec> filteredList = new ArrayList<>(kprime);
        for (WordVec wv : sublist) {
            if (wv.querySim > thresh)
                filteredList.add(wv);
        }        
        return filteredList;
    }
    
    public List<WordVec> getNearestNeighbors(String queryWord) {
        ArrayList<WordVec> distList = new ArrayList<>(wordvecmap.size());
        
        WordVec queryVec = wordvecmap.get(queryWord);
        if (queryVec == null)
            return null;
        
        for (Map.Entry<String, WordVec> entry : wordvecmap.entrySet()) {
            WordVec wv = entry.getValue();
            if (wv.word.equals(queryWord))
                continue;
            wv.querySim = queryVec.cosineSim(wv);
            distList.add(wv);
        }
        Collections.sort(distList);
        return distList.subList(0, Math.min(k, distList.size()));        
    }
    
    public WordVec getVec(String word) {
        return wordvecmap.get(word);
    }

    public float getSim(String u, String v) {
        WordVec uVec = wordvecmap.get(u);
        WordVec vVec = wordvecmap.get(v);
        if (uVec == null || vVec == null) {
            return 0;
        }

        return uVec.cosineSim(vVec);
    }
    
    private boolean isLegalToken(String word) {
        boolean flag = true;
        for (int i=0; i< word.length(); i++) {
            if(!isLetter(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    
    public void loadPrecomputedNNs() throws FileNotFoundException, IOException {
        nearestWordVecsMap = new HashMap<>();
        String NNDumpPath = prop.getProperty("NNDumpPath");
        if (NNDumpPath == null) {
            System.out.println("NNDumpPath not specified in configuration...");
            return;
        }
        System.out.println("Reading from the NN dump at: "+ NNDumpPath);
        File NNDumpFile = new File(NNDumpPath);
        
        try (FileReader fr = new FileReader(NNDumpFile);
            BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line, " \t:");
                List<String> tokens = new ArrayList<>();
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    tokens.add(token);
                }
                List<WordVec> nns = new LinkedList();
                int len = tokens.size();
                for (int i=1; i < len-1; i+=2) {
                    nns.add(new WordVec(tokens.get(i), Float.parseFloat(tokens.get(i+1))));
                }
                nearestWordVecsMap.put(tokens.get(0), nns);
            }
            System.out.println("NN dump has been reloaded");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            args[0] = "tweet.index.properties";
        }
        
        try {
            WordVecs qe = new WordVecs(args[0]);
            qe.computeAndStoreNNs();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
