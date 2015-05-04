/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package trec;

/**
 *
 * @author Debasis
 */
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import java.util.*;
import javax.xml.transform.*;
import org.apache.lucene.search.Query;
import tsm.WordVecSearcher;

public class TRECQueryParser extends DefaultHandler {
    StringBuffer        buff;      // Accumulation buffer for storing the current topic
    String              fileName;
    TRECQuery           query;
    WordVecSearcher     parent;
    
    public List<TRECQuery>  queries;
    final static String[] tags = {"num", "title", "desc", "narr"};

    public TRECQueryParser(String fileName, WordVecSearcher parent) throws SAXException {
       this.fileName = fileName;
       buff = new StringBuffer();
       queries = new LinkedList<>();
       this.parent = parent;
    }

    public void parse() throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        SAXParser saxParser = saxParserFactory.newSAXParser();
//        saxParserFactory.setValidating(false);
//        SAXParser saxParser = saxParserFactory.newSAXParser();
//        saxParser.parse(fileName, this);
        saxParser.parse(fileName, this);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (qName.equalsIgnoreCase("top"))
            query = new TRECQuery(this.parent);
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        //System.out.println(buff);
        if (qName.equalsIgnoreCase("title")) {
            query.title = buff.toString().trim();
            buff.setLength(0);
        }
        else if (qName.equalsIgnoreCase("desc")) {
            query.desc = buff.toString().trim();
            buff.setLength(0);
        }
        else if (qName.equalsIgnoreCase("num")) {
            query.id = buff.toString().trim();
            buff.setLength(0);
        }
        else if (qName.equalsIgnoreCase("narr")) {
            query.narr = buff.toString().trim();
            buff.setLength(0);
        }
        else if (qName.equalsIgnoreCase("top")) {
            queries.add(query);
        }        
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        buff.append(new String(ch, start, length));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            System.err.println("usage: java TRECQuery <input xml file>");
            args[0] = "/mnt/sdb2/research/wvlm/tweet/topics.microblog2011.xml";
        }

        try {
            TRECQueryParser parser = new TRECQueryParser(args[0], null);
            parser.parse();

            for (TRECQuery query : parser.queries) {
                System.out.println("ID: "+query.id);
                System.out.println("Title: "+query.title);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}    
