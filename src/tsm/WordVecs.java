package tsm;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
    HashMap<String, List<WordVec>> nearestWordVecsMap; // Store the pre-computed NNs
    
    public WordVecs(String propFile) throws Exception {
        
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        k = Integer.parseInt(prop.getProperty("wordvecs.numnearest", "5"));        
        String wordvecFile = prop.getProperty("wordvecs.vecfile");
        
        wordvecmap = new HashMap();
        try (FileReader fr = new FileReader(wordvecFile);
                BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                WordVec wv = new WordVec(line);
                wordvecmap.put(wv.word, wv);
            }
        }
    }

    public WordVecs(Properties prop) throws Exception {        
        k = Integer.parseInt(prop.getProperty("wordvecs.numnearest", "5"));        
        String wordvecFile = prop.getProperty("wordvecs.vecfile");
        
        wordvecmap = new HashMap();
        try (FileReader fr = new FileReader(wordvecFile);
                BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                WordVec wv = new WordVec(line);
                wordvecmap.put(wv.word, wv);
            }
        }
    }
    
    public void computeAndStoreNNs() {
        nearestWordVecsMap = new HashMap<>(wordvecmap.size());
        
        for (Map.Entry<String, WordVec> entry : wordvecmap.entrySet()) {
            WordVec wv = entry.getValue();
            List<WordVec> nns = getNearestNeighbors(wv.word);
            if (nns != null) {
                nearestWordVecsMap.put(wv.word, nns);
            }
        }
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
        return uVec.cosineSim(vVec);
    }
    
    public static void main(String[] args) {
        try {
            WordVecs qe = new WordVecs("init.properties");
            List<WordVec> nwords = qe.getNearestNeighbors("test");
            for (WordVec word : nwords) {
                System.out.println(word.word + "\t" + word.querySim);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
