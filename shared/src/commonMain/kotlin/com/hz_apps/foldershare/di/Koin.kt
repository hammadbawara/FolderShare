package com.hz_apps.foldershare.di

import com.hz_apps.foldershare.core.discovery.DeviceDiscoveryEngine
import com.hz_apps.foldershare.core.discovery.ServiceAdvertiser
import com.hz_apps.foldershare.core.discovery.ServiceBrowser
import com.hz_apps.foldershare.core.discovery.createDeviceDiscoveryEngine
import com.hz_apps.foldershare.core.explorer.repository.RemoteFileRepository
import com.hz_apps.foldershare.core.explorer.repository.WebDavRemoteFileRepository
import com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler
import com.hz_apps.foldershare.core.explorer.util.getPlatformFileHandler
import com.hz_apps.foldershare.core.server.ServerController
import com.hz_apps.foldershare.core.server.ServerManager
import com.hz_apps.foldershare.core.server.createServerController
import com.hz_apps.foldershare.data.database.AppDatabase
import com.hz_apps.foldershare.data.database.DeviceCredentialDao
import com.hz_apps.foldershare.data.database.FolderConfigDao
import com.hz_apps.foldershare.data.database.ServerConfigDao
import com.hz_apps.foldershare.data.database.getDatabaseBuilder
import com.hz_apps.foldershare.data.database.getRoomDatabase
import com.hz_apps.foldershare.feature.devices.DeviceAuthViewModel
import com.hz_apps.foldershare.feature.devices.DevicesViewModel
import com.hz_apps.foldershare.feature.explorer.FileExplorerViewModel
import com.hz_apps.foldershare.feature.settings.SettingsViewModel
import com.hz_apps.foldershare.feature.share.ShareViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val coreModule = module {
    single<AppDatabase> { getRoomDatabase(getDatabaseBuilder()) }
    single<FolderConfigDao> { get<AppDatabase>().folderConfigDao() }
    single<ServerConfigDao> { get<AppDatabase>().serverConfigDao() }
    single<DeviceCredentialDao> { get<AppDatabase>().deviceCredentialDao() }
    single<DeviceDiscoveryEngine> { createDeviceDiscoveryEngine() }
    single<ServiceAdvertiser> { get<DeviceDiscoveryEngine>() }
    single<ServiceBrowser> { get<DeviceDiscoveryEngine>() }
    single<com.hz_apps.foldershare.core.explorer.client.WebDavClient> { com.hz_apps.foldershare.core.explorer.client.KtorWebDavClient(com.hz_apps.foldershare.core.explorer.repository.createHttpClient()) }
    single<com.hz_apps.foldershare.core.discovery.DeviceResolver> { com.hz_apps.foldershare.core.discovery.DefaultDeviceResolver(get(), get()) }
    single<RemoteFileRepository> { WebDavRemoteFileRepository(get(), get(), get()) }
    single<com.hz_apps.foldershare.core.discovery.DeviceConnector> { com.hz_apps.foldershare.core.discovery.DefaultDeviceConnector(get(), get(), get()) }
    single<PlatformFileHandler> { getPlatformFileHandler() }
    single<ServerManager> { ServerManager(get(), get(), get()) }
    single<ServerController> { createServerController(get()) }
    single<com.hz_apps.foldershare.core.proxy.LocalStreamProxy> { com.hz_apps.foldershare.core.proxy.LocalStreamProxy(repository = get()) }
    single<com.hz_apps.foldershare.core.proxy.StreamProxyController> { com.hz_apps.foldershare.core.proxy.createStreamProxyController(get()) }
    single<com.hz_apps.foldershare.core.transfer.FileTransferController> { com.hz_apps.foldershare.core.transfer.createFileTransferController() }
    single<com.hz_apps.foldershare.core.transfer.FileTransferManager> { com.hz_apps.foldershare.core.transfer.FileTransferManager(get(),
        get(), get()) }
    single<com.hz_apps.foldershare.core.permissions.BackgroundPermissionHandler> { com.hz_apps.foldershare.core.permissions.createBackgroundPermissionHandler() }
}

val viewModelModule = module {
    viewModel { ShareViewModel(get(), get(), get(), get()) }
    viewModel { DevicesViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { FileExplorerViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { DeviceAuthViewModel(get(), get()) }
}

val appModule = listOf(coreModule, viewModelModule)

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication {
    com.hz_apps.foldershare.core.util.LoggingConfig.initLogging()
    return startKoin {
        appDeclaration()
        modules(appModule)
    }
}
