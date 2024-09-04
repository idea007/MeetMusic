package com.dafay.demo.lib.material.data;

object MockMusicSource {

    private val radioStream = "https://streaming.jamendo.com/JamPop"

    private val audio1 =
        "https://prod-1.storage.jamendo.com/?trackid=1875816&format=mp31&from=ToFegC%2Fu3q3xkYpQdp5fZQ%3D%3D%7CXf5pf2RzzWQyeVMp8%2Fh%2FRg%3D%3D"
    private val audio2 =
        "https://prod-1.storage.jamendo.com/?trackid=6719&format=mp31&from=7MqhAToIJIoNe4JIlMMt8A%3D%3D%7C2af5C%2BZYUYwnwCeAE9kmnA%3D%3D"
    private val audio3 =
        "https://prod-1.storage.jamendo.com/?trackid=1891011&format=mp31&from=cR5N9bKnkFzbzAiW%2Fbv%2BGw%3D%3D%7C4Nhu5A%2BGqkQvNw8aXSbS%2FA%3D%3D"
    private val audio4 =
        "https://prod-1.storage.jamendo.com/?trackid=1880317&format=mp31&from=DnXVPCVFs%2Fc0L9%2BIWTXXxQ%3D%3D%7C3LFi%2BxNWNT9RbZoBlMXxjw%3D%3D"

    private val audioList = arrayListOf(audio1, audio2, audio3, audio4)

     fun getAudioList(): List<String> {
        return audioList
    }

}
