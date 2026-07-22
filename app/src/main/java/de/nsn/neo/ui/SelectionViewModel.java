package de.nsn.neo.ui;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

/** Keeps navigation and stream selection stable across mobile rotation and view recreation. */
public final class SelectionViewModel extends ViewModel {
    private static final String CONTENT="content", SEASON="season", EPISODE="episode", LANGUAGE="language", HOSTER="hoster", POSITION="position";
    private final SavedStateHandle state;
    public SelectionViewModel(SavedStateHandle state) { this.state = state; }
    public String contentId() { return state.get(CONTENT); }
    public void selectContent(String value) { state.set(CONTENT, value); }
    public int season() { Integer v=state.get(SEASON); return v==null?1:v; }
    public void selectSeason(int value) { state.set(SEASON,value); }
    public String episodeId() { return state.get(EPISODE); }
    public void selectEpisode(String value) { state.set(EPISODE,value); }
    public String language() { return state.get(LANGUAGE); }
    public void selectLanguage(String value) { state.set(LANGUAGE,value); }
    public String hoster() { return state.get(HOSTER); }
    public void selectHoster(String value) { state.set(HOSTER,value); }
    public long positionMs() { Long v=state.get(POSITION); return v==null?0:v; }
    public void savePosition(long value) { state.set(POSITION,value); }
}
