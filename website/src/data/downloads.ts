// src/data/downloads.ts

export const GITHUB_OWNER = "hammadbawara";
export const GITHUB_REPO = "FolderShare";
export const GITHUB_REPO_URL = `https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}`;
export const GITHUB_RELEASES_URL = `${GITHUB_REPO_URL}/releases`;

/**
 * Workflow artifact naming patterns derived directly from:
 * - .github/workflows/build_android.yml -> FolderShare-${VERSION}-android.apk
 * - .github/workflows/build_linux.yml   -> FolderShare-${VERSION}-linux-amd64.deb, FolderShare-${VERSION}-linux-x64.tar.gz
 * - .github/workflows/build_windows.yml -> FolderShare-${VERSION}-windows-x64.exe, .msi, .zip
 */
export const ASSET_CONFIG = {
  apk: {
    suffix: "-android.apk",
    regex: /(-android\.apk|\.apk)$/i,
    name: "Universal Android APK",
    badge: ".apk",
    desc: "Direct package for Android phones, tablets & Android TV (Android 7.0+)",
    filenameTemplate: (ver: string) => `FolderShare-${ver}-android.apk`,
  },
  deb: {
    suffix: "-linux-amd64.deb",
    regex: /(-linux-amd64\.deb|\.deb)$/i,
    name: "DEB Package",
    badge: ".deb",
    desc: "Debian, Ubuntu, Linux Mint & derivatives (x86_64)",
    filenameTemplate: (ver: string) => `FolderShare-${ver}-linux-amd64.deb`,
  },
  targz: {
    suffix: "-linux-x64.tar.gz",
    regex: /(-linux-x64\.tar\.gz|\.tar\.gz)$/i,
    name: "Standalone TAR.GZ",
    badge: ".tar.gz",
    desc: "Universal standalone archive for all Linux distributions",
    filenameTemplate: (ver: string) => `FolderShare-${ver}-linux-x64.tar.gz`,
  },
  exe: {
    suffix: "-windows-x64.exe",
    regex: /(-windows-x64\.exe|\.exe)$/i,
    name: "Windows Setup (EXE)",
    badge: ".exe",
    desc: "Windows 10 & 11 64-bit setup installer",
    filenameTemplate: (ver: string) => `FolderShare-${ver}-windows-x64.exe`,
  },
  msi: {
    suffix: "-windows-x64.msi",
    regex: /(-windows-x64\.msi|\.msi)$/i,
    name: "MSI Installer",
    badge: ".msi",
    desc: "Windows 10 & 11 64-bit standalone installer",
    filenameTemplate: (ver: string) => `FolderShare-${ver}-windows-x64.msi`,
  },
  zip: {
    suffix: "-windows-x64.zip",
    regex: /(-windows-x64\.zip|\.zip)$/i,
    name: "Portable ZIP",
    badge: ".zip",
    desc: "Portable standalone folder — no installation required",
    filenameTemplate: (ver: string) => `FolderShare-${ver}-windows-x64.zip`,
  },
} as const;

export type AssetId = keyof typeof ASSET_CONFIG;

/**
 * Builds the artifact filename dynamically from the release version using the workflow pattern
 */
export function getBinaryFilename(assetId: AssetId, tagOrVersion: string): string {
  const version = tagOrVersion.replace(/^v/, "");
  const config = ASSET_CONFIG[assetId];
  return config ? config.filenameTemplate(version) : "";
}

/**
 * Generates direct download URL for a specific asset and release tag
 */
export function getBinaryDownloadUrl(assetId: AssetId, tag: string): string {
  const cleanTag = tag.startsWith("v") ? tag : `v${tag}`;
  const filename = getBinaryFilename(assetId, cleanTag);
  return `https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/releases/download/${cleanTag}/${filename}`;
}

/**
 * Generates all direct download URLs for any given release tag
 */
export function generateDownloadUrls(tag: string): Record<AssetId, string> {
  const urls = {} as Record<AssetId, string>;
  for (const key of Object.keys(ASSET_CONFIG) as AssetId[]) {
    urls[key] = getBinaryDownloadUrl(key, tag);
  }
  return urls;
}

export interface ResolvedRelease {
  tagName: string;
  version: string;
  isPrerelease: boolean;
  downloads: Record<AssetId, string>;
}

/**
 * Queries GitHub API preferring the latest stable release,
 * but falling back to the latest pre-release if no stable exists yet.
 */
export async function fetchTargetRelease(): Promise<ResolvedRelease | null> {
  const headers = {
    Accept: "application/vnd.github.v3+json",
    "User-Agent": "FolderShare-Website",
  };

  // 1. Try /releases/latest first (GitHub only returns stable releases from here)
  try {
    const res = await fetch(`https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/latest`, {
      headers,
    });
    if (res.ok) {
      const data = await res.json();
      if (data && !data.prerelease && !data.draft && data.tag_name) {
        return {
          tagName: data.tag_name,
          version: data.tag_name.replace(/^v/, ""),
          isPrerelease: false,
          downloads: generateDownloadUrls(data.tag_name),
        };
      }
    }
  } catch {
    // Continue to /releases fallback
  }

  // 2. Query release list: find latest stable or fallback to latest pre-release
  try {
    const res = await fetch(`https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases?per_page=10`, {
      headers,
    });
    if (res.ok) {
      const list = await res.json();
      if (Array.isArray(list) && list.length > 0) {
        const stable = list.find((r: any) => !r.prerelease && !r.draft && r.tag_name);
        const target = stable || list.find((r: any) => !r.draft && r.tag_name);
        if (target) {
          return {
            tagName: target.tag_name,
            version: target.tag_name.replace(/^v/, ""),
            isPrerelease: Boolean(target.prerelease),
            downloads: generateDownloadUrls(target.tag_name),
          };
        }
      }
    }
  } catch {
    // Network or rate-limit error
  }

  return null;
}
