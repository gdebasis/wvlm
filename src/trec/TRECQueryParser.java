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

public class TRECQueryParser extends DefaultHandler {
    StringBuffer        buff;      // Accumulation buffer for storing the current topic
    String              fileName;
    TRECQuery           query;
    
    public List<TRECQuery>  queries;
    final static String[] tags = {"id", "title", "desc", "narr"};

    public TRECQueryParser(String fileName) throws SAXException {
       this.fileName = fileName;
       buff = new StringBuffer();
       queries = new LinkedList<>();
    }

    public void parse() throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setValidating(false);
        SAXParser saxParser = saxParserFactory.newSAXParser();
        saxParser.parse(fileName, this);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (qName.equalsIgnoreCase("query"))
            query = new TRECQuery();
        else
            buff = new StringBuffer();
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        if (qName.equalsIgnoreCase("title"))
            query.title = buff.toString();
        else if (qName.equalsIgnoreCase("desc"))
            query.desc = buff.toString();
        else if (qName.equalsIgnoreCase("narr"))
            query.narr = buff.toString();
        else if (qName.equalsIgnoreCase("id"))
            query.id = buff.toString();        
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        buff.append(new String(ch, start, length));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: java TRECQuery <input xml file>");
            return;
        }

        try {
            TRECQueryParser parser = new TRECQueryParser(args[0]);
            parser.parse();
            for (TRECQuery q : parser.queries) {
                System.out.println(q);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}    
