package com.github.asforest.model

class IndexResponse
{
    lateinit var serverVersion: String
    lateinit var serverType: String
    lateinit var mode: String
    lateinit var paths: Array<String>

    lateinit var updateUrl: String
    lateinit var updateSource: String
}