package jp.outlook.rekih.googlephotoslider

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AlbumSelect : ViewModel() {
    val albumList: MutableLiveData<List<Album>> by lazy { MutableLiveData<List<Album>>() }

    fun loadAlbumList() {
        viewModelScope.launch {
            albumList.value = GooglePhotoApi.getAlbumList().filter{it.mediaItemsCount.isNotEmpty()}.toList()
        }
    }
}