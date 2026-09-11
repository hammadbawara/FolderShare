// src/scripts/release-resolver.ts
import { ASSET_CONFIG, getBinaryDownloadUrl, type AssetId } from '../data/downloads';

interface ReleaseAsset {
  name: string;
  browser_download_url: string;
  size?: number;
}

interface ReleaseData {
  tag_name: string;
  name: string;
  prerelease: boolean;
  draft: boolean;
  published_at?: string;
  assets?: ReleaseAsset[];
}

interface CachedReleaseInfo {
  timestamp: number;
  tagName: string;
  isPrerelease: boolean;
  urls: Record<string, string>;
}

const CACHE_KEY = "foldershare_resolved_releases_v1";
const CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

function applyReleaseToDom(tagName: string, isPrerelease: boolean, urls: Record<string, string>) {
  // 1. Update all download links with direct binary download URLs
  document.querySelectorAll<HTMLAnchorElement>("a[data-asset-id]").forEach((link) => {
    const assetId = link.getAttribute("data-asset-id");
    if (assetId && urls[assetId]) {
      link.href = urls[assetId];
    }
  });

  // 2. Update version tags
  document.querySelectorAll<HTMLElement>("[data-release-tag]").forEach((el) => {
    el.textContent = tagName;
  });

  // 3. Update release status badges
  document.querySelectorAll<HTMLElement>("[data-release-badge]").forEach((el) => {
    el.textContent = isPrerelease ? "Pre-release" : "Latest Stable";
  });

  // 4. Update full release labels (e.g. "v0.1.0 (Latest Stable)")
  document.querySelectorAll<HTMLElement>("[data-release-label]").forEach((el) => {
    el.textContent = `${tagName} (${isPrerelease ? "Pre-release" : "Latest Stable"})`;
  });
}

function extractAssetUrls(release: ReleaseData): Record<string, string> {
  const urls: Record<string, string> = {};
  const tagName = release.tag_name;

  for (const key of Object.keys(ASSET_CONFIG) as AssetId[]) {
    const config = ASSET_CONFIG[key];
    const match = release.assets?.find((a) => config.regex.test(a.name));
    if (match && match.browser_download_url) {
      urls[key] = match.browser_download_url;
    } else if (tagName) {
      // Fallback to pattern-generated direct download URL from workflow specification
      urls[key] = getBinaryDownloadUrl(key, tagName);
    }
  }

  return urls;
}

export async function resolveDirectDownloads() {
  if (typeof window === "undefined") return;

  // Check sessionStorage cache first to prevent hitting GitHub unauthenticated rate limit
  try {
    const cachedStr = sessionStorage.getItem(CACHE_KEY);
    if (cachedStr) {
      const cached: CachedReleaseInfo = JSON.parse(cachedStr);
      if (Date.now() - cached.timestamp < CACHE_TTL_MS) {
        applyReleaseToDom(cached.tagName, cached.isPrerelease, cached.urls);
        return;
      }
    }
  } catch {
    // Ignore sessionStorage access errors
  }

  try {
    let targetRelease: ReleaseData | null = null;

    // Step 1: Try /releases/latest first.
    // GitHub only returns stable (non-prerelease, non-draft) releases from this endpoint.
    try {
      const resLatest = await fetch("https://api.github.com/repos/hammadbawara/FolderShare/releases/latest", {
        headers: { Accept: "application/vnd.github.v3+json" },
      });
      if (resLatest.ok) {
        const latestData: ReleaseData = await resLatest.json();
        if (latestData && !latestData.prerelease && !latestData.draft && latestData.tag_name) {
          targetRelease = latestData;
        }
      }
    } catch {
      // Ignore network errors on /latest check and fall through to /releases
    }

    // Step 2: If no stable release from /releases/latest, query the releases list
    if (!targetRelease) {
      const resList = await fetch("https://api.github.com/repos/hammadbawara/FolderShare/releases?per_page=10", {
        headers: { Accept: "application/vnd.github.v3+json" },
      });

      if (resList.ok) {
        const list: ReleaseData[] = await resList.json();
        if (Array.isArray(list) && list.length > 0) {
          // A) Always prefer latest stable release if one exists
          const stable = list.find((r) => !r.prerelease && !r.draft && r.tag_name);
          if (stable) {
            targetRelease = stable;
          } else {
            // B) If no stable release exists at all yet, fall back to the latest pre-release
            const prerelease = list.find((r) => !r.draft && r.tag_name);
            if (prerelease) {
              targetRelease = prerelease;
            }
          }
        }
      }
    }

    if (!targetRelease || !targetRelease.tag_name) return;

    const urls = extractAssetUrls(targetRelease);
    const tagName = targetRelease.tag_name;
    const isPrerelease = Boolean(targetRelease.prerelease);

    applyReleaseToDom(tagName, isPrerelease, urls);

    // Cache successful resolution
    try {
      const cachePayload: CachedReleaseInfo = {
        timestamp: Date.now(),
        tagName,
        isPrerelease,
        urls,
      };
      sessionStorage.setItem(CACHE_KEY, JSON.stringify(cachePayload));
    } catch {
      // Ignore cache write errors
    }
  } catch (err) {
    // Retain default static fallback URLs without breaking user experience
    console.warn("FolderShare: Using fallback direct download links", err);
  }
}

// Auto-run if executed in a browser environment
if (typeof window !== "undefined") {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      resolveDirectDownloads();
    });
  } else {
    resolveDirectDownloads();
  }
}
