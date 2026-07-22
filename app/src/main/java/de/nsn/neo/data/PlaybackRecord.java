package de.nsn.neo.data;
import de.nsn.neo.model.SourceId;
public final class PlaybackRecord {
 public final SourceId source; public final String contentId,episodeId,title,subtitle,posterUrl; public final long positionMs,durationMs,updatedAt;
 public PlaybackRecord(SourceId source,String contentId,String episodeId,String title,String subtitle,String posterUrl,long positionMs,long durationMs,long updatedAt){this.source=source;this.contentId=contentId;this.episodeId=episodeId;this.title=title;this.subtitle=subtitle;this.posterUrl=posterUrl;this.positionMs=positionMs;this.durationMs=durationMs;this.updatedAt=updatedAt;}
 public float progress(){return durationMs<=0?0f:Math.max(0f,Math.min(1f,(float)positionMs/durationMs));}
}
