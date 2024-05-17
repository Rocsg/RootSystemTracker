package io.github.rocsg.rsmlparser;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.github.rocsg.rsmlparser.RsmlParser.getRSMLsinfos;

public class TemproalRSMLParser {
    public String path2RSMLs;
    Map<Date, List<RootModel4Parser>> mapRSMLParsers;


    public TemproalRSMLParser(String path2RSMLs) throws IOException {
        this.path2RSMLs = path2RSMLs;
        this.mapRSMLParsers = getRSMLsinfos(Paths.get(path2RSMLs));
    }

    public static void main(String[] args) {
        try {
            TemproalRSMLParser temproalRSMLParser = new TemproalRSMLParser("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\");
            System.out.println(temproalRSMLParser.mapRSMLParsers);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


