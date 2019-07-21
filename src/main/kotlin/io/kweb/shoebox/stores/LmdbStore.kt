package io.kweb.shoebox.stores

import com.fatboyindustrial.gsonjavatime.Converters
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import io.kweb.shoebox.*
import java.nio.file.*
import kotlin.reflect.KClass

import org.lmdbjava.*
import java.nio.ByteBuffer
import java.nio.ByteBuffer.allocateDirect
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.io.FileSystemException


/**
 * Uses LMDB for storage.
 *
 * TODO: remove dependence on gson
 */

inline fun <reified T : Any> LmdbStore(lmdbEnv: LmdbEnv, name: String) = LmdbStore(lmdbEnv, name, T::class)


class LmdbStore<T : Any>(var lmdbEnv: LmdbEnv, val name: String, private val kc: KClass<T>, val gson: Gson = defaultGson) : Store<T> {

    private val env = lmdbEnv.env
    private val dbi: Dbi<ByteBuffer> = env.openDbi(name, DbiFlags.MDB_CREATE)

    /**
     * Retrieve the entries in this store, similar to [Map.entries] but lazy
     *
     * @return The keys and their corresponding values in this [Shoebox]
     */
    override val entries: Iterable<KeyValue<T>> get() {
        val ret = mutableSetOf<KeyValue<T>>()
        env.txnRead().use { txn ->
            dbi.iterate(txn).use { c ->
                c.forEach {
                    val k = UTF_8.decode(it.key()).toString()
                    val v = gson.fromJson(UTF_8.decode(it.`val`()).toString(), kc.javaObjectType)
                    ret.add(KeyValue(k, v))
                }
            }
            txn.abort()
        }
        return ret
    }

    /**
     * Retrieve a value, similar to [Map.get]
     *
     * @param key The key associated with the desired value
     * @return The value associated with the key, or null if no value is associated
     */
    override operator fun get(key: String): T? {
        require(key.isNotBlank()) {"key(\"$key\") must not be blank"}
        val k = allocateDirect(env.maxKeySize)
        k.put(key.toByteArray(UTF_8)).flip()
        var ret: T? = null
        env.txnRead().use { txn ->
            val v: ByteBuffer? = dbi.get(txn, k)
            if (v != null) {
                ret = gson.fromJson(UTF_8.decode(v).toString(), kc.javaObjectType)
            }
            txn.abort()
        }
        return ret
    }

    /**
     * Remove a key-value pair
     *
     * @param key The key associated with the value to be removed, similar to [MutableMap.remove]
     */
    override fun remove(key: String) : T? {
        require(key.isNotBlank()) {"key(\"$key\") must not be blank"}
        val k = allocateDirect(env.maxKeySize)
        k.put(key.toByteArray(UTF_8)).flip()
        var ret: T? = null
        env.txnWrite().use { txn ->
            // who needs the value?
            val oldv: ByteBuffer? = dbi.get(txn, k)
            if (oldv != null) {
                ret = gson.fromJson(UTF_8.decode(oldv).toString(), kc.javaObjectType)
            }
            dbi.delete(txn, k)
            txn.commit()
        }
        return ret
    }

    /**
     * Set or change a value, simliar to [MutableMap.set]
     *
     * @param key The key associated with the value to be set or changed
     * @param value The new value
     */
    override operator fun set(key: String, value: T) : T? {
        require(key.isNotBlank()) {"key(\"$key\") must not be blank"}
        val k = allocateDirect(env.maxKeySize)
        k.put(key.toByteArray(UTF_8)).flip()
        val bytes = gson.toJson(value, kc.javaObjectType).toByteArray(UTF_8)
        val v = allocateDirect(bytes.size)
        v.put(bytes).flip()
        var ret: T? = null
        env.txnWrite().use { txn ->
            // is the old value necessary?
            val oldv: ByteBuffer? = dbi.get(txn, k)
            if (oldv != null) {
                ret = gson.fromJson(UTF_8.decode(oldv).toString(), kc.javaObjectType)
            }
            dbi.put(txn, k, v)
            txn.commit()
        }
        return ret
    }

    protected fun finalize() {
        dbi.close()
    }

}

/*
 * Database environment, wraps org.lmdbjava.Env<ByteBuffer>.
 */

class LmdbEnv(val env: Env<ByteBuffer>) {

    companion object {

        /*
         * Creates new environment. By default, keys are limited to 512 bytes.
         * Default mmap size is very low to ensure attention.
         * @see
         *
         * @param path  file system destination
         * @param mapSize  size in bytes
         * @param maxDbs  maximum number of named stores
         */
        fun create(path: Path, mapSize: Long = 1048576, maxDbs: Int = 64): LmdbEnv {
            val file = path.toFile()
            if (!file.exists()) {
                if (!file.mkdir()) {
                    throw FileSystemException(file, reason = "Failed to create LMDB database directory!")
                }
            } else {
                if (!file.isDirectory) {
                    throw InvalidPathException("Not a directory", path.toString())
                }
            }
            return LmdbEnv(Env.create().setMapSize(mapSize).setMaxDbs(maxDbs).open(file))
        }

    }

    protected fun finalize() {
        env.close()
    }

}
