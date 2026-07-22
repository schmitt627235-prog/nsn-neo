package de.nsn.neo.source.filmpalast;

public final class FilmpalastSeriesFilterTest {
    public static void main(String[] args) {
        rejects("Man on Fire S01E04", "/stream/man-on-fire-s01e04", "Man.on.Fire.S01E04.German", "");
        rejects("Silo S3E3", "/stream/silo-s3e3", "", "");
        rejects("Test", "/serien/view/test", "", "Serien");
        rejects("Test Staffel 2 Episode 12", "/stream/test", "", "");
        accepts("Se7en", "/stream/se7en", "Se7en.1995.German", "Film");
        accepts("Mission: Impossible 3", "/stream/mission-impossible-3", "", "Filme");
        accepts("S1m0ne", "/stream/s1m0ne", "", "Film");
    }
    private static void rejects(String t,String u,String r,String c){if(!FilmpalastSeriesFilter.isSeries(t,u,r,c))throw new AssertionError("not rejected: "+t);}
    private static void accepts(String t,String u,String r,String c){if(FilmpalastSeriesFilter.isSeries(t,u,r,c))throw new AssertionError("false positive: "+t);}
}
