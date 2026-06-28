package app.mystery0.nodeflow.core.parser

import org.koin.dsl.module

val parserModule = module {
    single {
        V2exHtmlParser()
    }
}
