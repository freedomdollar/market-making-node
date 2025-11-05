package com.zanable.marketmaking.bot.beans;

import lombok.Getter;

import java.util.List;

@Getter
public class GitHubRelease {
    public String url;
    public String assets_url;
    public String upload_url;
    public String html_url;
    public long id;
    public Author author;
    public String node_id;
    public String tag_name;
    public String target_commitish;
    public String name;
    public boolean draft;
    public boolean immutable;
    public boolean prerelease;
    public String created_at;
    public String updated_at;
    public String published_at;
    public List<Object> assets; // You can define a proper Asset class if needed
    public String tarball_url;
    public String zipball_url;
    public String body;
}
