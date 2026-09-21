package com.echo.recall.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ModelCatalog] 分级与推荐逻辑测试。
 *
 * 这部分是纯逻辑（不碰 Android API），必须锁死，否则「推荐错档」会直接
 * 让低端机用户下不动模型。
 */
class ModelCatalogTest {

    private fun device(cores: Int, memoryClassMb: Int, lowRam: Boolean = false) =
        ModelCatalog.DeviceTier(cores = cores, memoryClassMb = memoryClassMb, lowRam = lowRam)

    // ---------------------------------------------------------------- 目录完整性

    @Test
    fun `catalog has exactly three tiers`() {
        assertEquals("v1.1 只上 L1/L2/L3 三档（L4 已砍）", 3, ModelCatalog.all.size)
        assertEquals(
            listOf(AsrModelTier.L1_LIGHT, AsrModelTier.L2_BALANCED, AsrModelTier.L3_ACCURATE),
            ModelCatalog.all.map { it.tier },
        )
    }

    @Test
    fun `tier order is ascending and unique`() {
        assertEquals(listOf(1, 2, 3), ModelCatalog.all.map { it.tier.order })
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.tier }.distinct().size)
    }

    @Test
    fun `model ids are unique`() {
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.distinct().size)
    }

    @Test
    fun `byId resolves every catalog entry and rejects unknown`() {
        ModelCatalog.all.forEach { assertEquals(it, ModelCatalog.byId(it.id)) }
        assertNull(ModelCatalog.byId("does-not-exist"))
    }

    @Test
    fun `legacy id points at the sense voice entry`() {
        // v1.0 用户升级后必须能映射回同一个模型
        assertEquals(ModelCatalog.L3_SENSE_VOICE, ModelCatalog.byId(ModelCatalog.LEGACY_DEFAULT_ID))
    }

    @Test
    fun `every model has a tokens file and at least one url per file`() {
        ModelCatalog.all.forEach { spec ->
            assertTrue(
                "${spec.id} 必须有 tokens.txt",
                spec.files.any { it.name == "tokens.txt" },
            )
            spec.files.forEach { f ->
                assertTrue("${spec.id}/${f.name} 缺少下载地址", f.urls.isNotEmpty())
                assertTrue("${spec.id}/${f.name} 体积应为正", f.sizeBytes > 0)
                assertEquals(
                    "${spec.id}/${f.name} 必须有 64 位十六进制 sha256",
                    64, f.sha256.length,
                )
                assertTrue(
                    "${spec.id}/${f.name} sha256 必须是十六进制",
                    f.sha256.all { it in "0123456789abcdef" },
                )
            }
        }
    }

    @Test
    fun `every file url is https and points at a verified mirror host`() {
        ModelCatalog.all.forEach { spec ->
            spec.files.forEach { f ->
                f.urls.forEach { url ->
                    assertTrue("$url 必须是 https", url.startsWith("https://"))
                    val known = url.startsWith("https://hf-mirror.com/") ||
                        url.startsWith("https://huggingface.co/") ||
                        url.startsWith("https://modelscope.cn/")
                    assertTrue("$url 不在已验证的镜像白名单内", known)
                }
            }
        }
    }

    @Test
    fun `every file offers two mainland-china reachable sources`() {
        // 国内用户必须有两条独立通道：hf-mirror 与 modelscope 都是境内可达的独立基础设施
        ModelCatalog.all.forEach { spec ->
            spec.files.forEach { f ->
                assertTrue(
                    "${spec.id}/${f.name} 缺少 hf-mirror 源",
                    f.urls.any { it.startsWith("https://hf-mirror.com/") },
                )
                assertTrue(
                    "${spec.id}/${f.name} 缺少 modelscope 兜底源",
                    f.urls.any { it.startsWith("https://modelscope.cn/") },
                )
                assertTrue(
                    "${spec.id}/${f.name} 缺少官方站源",
                    f.urls.any { it.startsWith("https://huggingface.co/") },
                )
            }
        }
    }

    @Test
    fun `mirror order is hf-mirror first then modelscope then official`() {
        // 顺序即优先级：hf-mirror 实测最健康，放第一
        ModelCatalog.all.forEach { spec ->
            spec.files.forEach { f ->
                assertEquals(
                    "${spec.id}/${f.name} 的首选源应为 hf-mirror",
                    true, f.urls.first().startsWith("https://hf-mirror.com/"),
                )
                assertEquals(
                    "${spec.id}/${f.name} 的末位应为官方站",
                    true, f.urls.last().startsWith("https://huggingface.co/"),
                )
            }
        }
    }

    @Test
    fun `modelscope urls use the resolve master shape`() {
        // 实测正确的直链形状；用 api/v1 形式也可以，但这里统一走 resolve
        ModelCatalog.all.forEach { spec ->
            spec.files.forEach { f ->
                f.urls.filter { it.contains("modelscope") }.forEach { url ->
                    assertTrue("$url 形状应为 /models/<ns>/<model>/resolve/master/<file>",
                        url.contains("/resolve/master/"))
                }
            }
        }
    }

    @Test
    fun `modelscope url keeps the same file name as the official one`() {
        ModelCatalog.all.forEach { spec ->
            spec.files.forEach { f ->
                f.urls.forEach { url ->
                    assertTrue(
                        "${spec.id}/${f.name}: 各镜像的文件名必须一致（否则会下到不同文件）",
                        url.endsWith("/${f.name}"),
                    )
                }
            }
        }
    }

    @Test
    fun `total bytes matches the sum of file sizes`() {
        ModelCatalog.all.forEach { spec ->
            assertEquals(spec.files.sumOf { it.sizeBytes }, spec.totalBytes)
        }
    }

    @Test
    fun `tiers are ordered by size as documented`() {
        val l1 = ModelCatalog.L1_ZIPFORMER_14M.totalBytes
        val l2 = ModelCatalog.L2_PARAFORMER_SMALL.totalBytes
        val l3 = ModelCatalog.L3_SENSE_VOICE.totalBytes
        assertTrue("L1 应显著小于 L2", l1 < l2)
        assertTrue("L2 应显著小于 L3", l2 < l3)
        // L2 的卖点就是「约为 L3 的 1/3」
        assertTrue("L2 应约为 L3 的三分之一（L2=$l2 L3=$l3）", l2 < l3 / 2)
    }

    @Test
    fun `documented megabyte figures match the byte counts`() {
        // 这些数字会直接显示在 UI 上（spec.totalMb()），必须与真实字节数一致
        assertEquals(29L, ModelCatalog.L1_ZIPFORMER_14M.totalMb())    // 约 29MB
        assertEquals(78L, ModelCatalog.L2_PARAFORMER_SMALL.totalMb()) // 约 78MB
        assertEquals(228L, ModelCatalog.L3_SENSE_VOICE.totalMb())     // 约 228MB
    }

    @Test
    fun `only sense voice advertises emotion tags`() {
        assertTrue(ModelCatalog.L3_SENSE_VOICE.supportsEmotion)
        assertTrue(!ModelCatalog.L1_ZIPFORMER_14M.supportsEmotion)
        assertTrue(!ModelCatalog.L2_PARAFORMER_SMALL.supportsEmotion)
    }

    @Test
    fun `only the light tier advertises streaming`() {
        assertTrue(ModelCatalog.L1_ZIPFORMER_14M.supportsStreaming)
        assertTrue(!ModelCatalog.L2_PARAFORMER_SMALL.supportsStreaming)
        assertTrue(!ModelCatalog.L3_SENSE_VOICE.supportsStreaming)
    }

    @Test
    fun `kinds map to the engines we actually implement`() {
        assertEquals(AsrModelKind.ZIPFORMER_STREAM, ModelCatalog.L1_ZIPFORMER_14M.kind)
        assertEquals(AsrModelKind.PARAFORMER, ModelCatalog.L2_PARAFORMER_SMALL.kind)
        assertEquals(AsrModelKind.SENSE_VOICE, ModelCatalog.L3_SENSE_VOICE.kind)
    }

    @Test
    fun `model directories are unique so models can coexist`() {
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.dirName }.distinct().size)
    }

    @Test
    fun `legacy on-disk directory is preserved for migration`() {
        // v1.0 把模型放在 filesDir/models/sense-voice，升级后必须仍然命中
        assertEquals("sense-voice", ModelCatalog.L3_SENSE_VOICE.dirName)
    }

    @Test
    fun `every model documents a non blank accuracy note`() {
        ModelCatalog.all.forEach {
            assertTrue("${it.id} 需要给用户一句诚实的准确率说明", it.accuracyNote.isNotBlank())
            assertTrue("${it.id} 需要标注语种", it.languages.isNotBlank())
            assertTrue("${it.id} 需要显示名", it.displayName.isNotBlank())
        }
    }

    // ---------------------------------------------------------------- 推荐逻辑

    @Test
    fun `low ram device gets the light tier`() {
        val spec = ModelCatalog.recommend(device(cores = 8, memoryClassMb = 512, lowRam = true))
        assertEquals(ModelCatalog.L1_ZIPFORMER_14M, spec)
    }

    @Test
    fun `device with fewer than four cores gets the light tier`() {
        assertEquals(
            ModelCatalog.L1_ZIPFORMER_14M,
            ModelCatalog.recommend(device(cores = 2, memoryClassMb = 512)),
        )
        assertEquals(
            ModelCatalog.L1_ZIPFORMER_14M,
            ModelCatalog.recommend(device(cores = 3, memoryClassMb = 512)),
        )
    }

    @Test
    fun `tiny heap gets the light tier`() {
        // 堆上限过小时，228MB 模型根本加载不进来
        assertEquals(
            ModelCatalog.L1_ZIPFORMER_14M,
            ModelCatalog.recommend(device(cores = 8, memoryClassMb = 128)),
        )
    }

    @Test
    fun `flagship device gets the accurate tier`() {
        val spec = ModelCatalog.recommend(device(cores = 8, memoryClassMb = 512))
        assertEquals(ModelCatalog.L3_SENSE_VOICE, spec)
    }

    @Test
    fun `mid range device gets the balanced tier`() {
        assertEquals(
            ModelCatalog.L2_PARAFORMER_SMALL,
            ModelCatalog.recommend(device(cores = 6, memoryClassMb = 256)),
        )
        // 8 核但堆上限不够大 → 仍然 L2
        assertEquals(
            ModelCatalog.L2_PARAFORMER_SMALL,
            ModelCatalog.recommend(device(cores = 8, memoryClassMb = 256)),
        )
    }

    @Test
    fun `recommendation is total - never returns null for odd inputs`() {
        val odd = listOf(
            device(0, 0),
            device(-1, -1),
            device(1, 1),
            device(64, 4096),
            device(4, 192),
        )
        odd.forEach { d ->
            assertNotNull("device=$d 必须有推荐结果", ModelCatalog.recommend(d))
        }
    }

    @Test
    fun `recommendation is monotonic in device power`() {
        // 更强的设备不应得到更低的档位
        val weak = ModelCatalog.recommend(device(2, 96, lowRam = true))
        val mid = ModelCatalog.recommend(device(6, 256))
        val strong = ModelCatalog.recommend(device(8, 512))
        assertTrue(weak.tier.order <= mid.tier.order)
        assertTrue(mid.tier.order <= strong.tier.order)
    }

    @Test
    fun `recommend reason is non blank and mentions the device for every tier`() {
        listOf(
            ModelCatalog.L1_ZIPFORMER_14M,
            ModelCatalog.L2_PARAFORMER_SMALL,
            ModelCatalog.L3_SENSE_VOICE,
        ).forEach { spec ->
            val reason = ModelCatalog.recommendReason(device(8, 512), spec)
            assertTrue("$spec 的推荐理由不能为空", reason.isNotBlank())
            assertTrue("推荐理由应说明设备情况：$reason", reason.contains("核"))
        }
    }

    // ---------------------------------------------------------------- 线程数

    @Test
    fun `thread count scales with cores but is capped at four`() {
        assertEquals(2, ModelCatalog.threadsFor(1))
        assertEquals(2, ModelCatalog.threadsFor(4))
        assertEquals(3, ModelCatalog.threadsFor(6))
        assertEquals(4, ModelCatalog.threadsFor(8))
        assertEquals("上限必须是 4（超过后收益递减而功耗上升）", 4, ModelCatalog.threadsFor(16))
        assertEquals(4, ModelCatalog.threadsFor(64))
    }

    @Test
    fun `thread count never drops below two`() {
        assertEquals(2, ModelCatalog.threadsFor(0))
        assertEquals(2, ModelCatalog.threadsFor(-5))
    }

    // ---------------------------------------------------------------- 设备摘要

    @Test
    fun `device summary mentions cores and memory`() {
        val s = device(8, 512).summary()
        assertTrue(s.contains("8"))
        assertTrue(s.contains("512"))
    }

    @Test
    fun `device summary flags low ram devices`() {
        assertTrue(device(4, 128, lowRam = true).summary().contains("低内存"))
        assertTrue(!device(8, 512, lowRam = false).summary().contains("低内存"))
    }
}
