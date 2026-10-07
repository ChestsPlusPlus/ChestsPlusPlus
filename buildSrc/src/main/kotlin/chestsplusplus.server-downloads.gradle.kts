// The pinned server and plugin jars the test servers run, kept in serverCacheDir.

tasks.register<DownloadFile>("downloadE2ePaper") {
    url = libs.versions.paperServerUrl
    checksum = "sha256:" + libs.versions.paperServerSha256.get()
    destination = File(serverCacheDir, "paper-${libs.versions.paperServer.get()}-${libs.versions.paperServerBuild.get()}.jar")
}

tasks.register<DownloadFile>("downloadV2Paper") {
    url = libs.versions.v2PaperServerUrl
    checksum = "sha256:" + libs.versions.v2PaperServerSha256.get()
    destination = File(serverCacheDir, "paper-${libs.versions.v2PaperServer.get()}-${libs.versions.v2PaperServerBuild.get()}.jar")
}

tasks.register<DownloadFile>("downloadViaVersion") {
    url = libs.versions.viaversionUrl
    checksum = "sha512:" + libs.versions.viaversionSha512.get()
    destination = File(serverCacheDir, "ViaVersion-${libs.versions.viaversion.get()}.jar")
}

tasks.register<DownloadFile>("downloadViaBackwards") {
    url = libs.versions.viabackwardsUrl
    checksum = "sha512:" + libs.versions.viabackwardsSha512.get()
    destination = File(serverCacheDir, "ViaBackwards-${libs.versions.viabackwards.get()}.jar")
}
