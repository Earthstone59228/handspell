package dev.handspell.app.vision.classify

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real shipped asset vs numpy forward pass over saved team live vectors.
 * Expected arrays: run training/scripts/generate_mlp_asset_parity.py from the repo root.
 * CSV row indices are zero-based after the header. The script reads runs/mlp-v1.npz.
 */
class MlpAssetParityTest {
    private val rows = intArrayOf(6, 16, 33, 50, 96, 97)

    private val expected = arrayOf(
        // CSV row 6: A
        doubleArrayOf(0.9988049621, 5.69853372e-14, 7.443987479e-25, 1.441053214e-12, 9.129905351e-09, 1.13201051e-12, 2.785969207e-07, 9.574987629e-11, 5.158044478e-08, 1.269186072e-06, 3.493192501e-21, 5.357368662e-12, 8.296564855e-06, 1.17321384e-07, 4.955988827e-14, 3.139131164e-12, 7.660411134e-09, 0.001049020216, 0.0001356369927, 1.758466278e-11, 2.866517937e-20, 2.086879856e-11, 3.419862051e-07, 8.510031738e-09),
        // CSV row 16: A
        doubleArrayOf(0.9999593703, 1.893591047e-15, 1.003542192e-22, 3.640377445e-12, 3.633140204e-08, 3.402542e-11, 5.78938052e-08, 2.178363429e-11, 1.592501894e-08, 3.716475945e-11, 2.276070346e-16, 1.886748031e-15, 2.872144588e-12, 3.014854943e-06, 3.925007097e-13, 2.983830528e-15, 9.098996102e-12, 3.664764557e-05, 2.949376838e-08, 6.503046285e-19, 1.596816334e-28, 3.11657306e-15, 3.681826693e-09, 8.237848692e-07),
        // CSV row 33: B
        doubleArrayOf(3.490587453e-16, 0.9990839479, 1.22470555e-06, 6.568714993e-14, 9.642745679e-15, 0.0009148273982, 1.178955342e-31, 1.281769805e-29, 1.175296072e-22, 2.057057161e-35, 9.436540759e-26, 3.075704116e-72, 3.750422744e-64, 1.631774079e-25, 4.280737588e-32, 3.605843198e-29, 9.69114925e-20, 3.048230964e-44, 1.591942026e-57, 6.464805683e-36, 9.77571782e-44, 1.420761547e-18, 6.066581848e-32, 8.50364492e-22),
        // CSV row 50: C
        doubleArrayOf(5.907387905e-17, 1.110989887e-05, 0.9999627418, 1.096670899e-11, 1.713128163e-05, 9.017008372e-06, 1.749962966e-27, 2.15083898e-21, 5.715138792e-14, 4.16166617e-24, 1.83523821e-17, 4.59046319e-45, 2.856984304e-47, 1.939227823e-17, 7.64896264e-19, 1.298254487e-19, 2.303259479e-13, 1.309074515e-28, 1.882344791e-45, 6.338340137e-26, 5.362198317e-35, 7.729557772e-16, 2.225331037e-18, 8.406143143e-17),
        // CSV row 96: D
        doubleArrayOf(2.043140047e-09, 1.502129612e-14, 4.249794985e-22, 0.9982623774, 1.694683819e-10, 3.552788695e-19, 3.840099181e-11, 4.450748651e-16, 4.272802804e-11, 4.636234537e-06, 2.100850838e-06, 2.904729955e-17, 8.005621394e-17, 1.506499461e-09, 7.201906092e-11, 6.523506197e-19, 2.021231561e-08, 4.292325672e-10, 1.531812273e-06, 1.067952838e-10, 5.344377136e-17, 1.669945873e-13, 0.001729329029, 2.652441983e-11),
        // CSV row 97: D
        doubleArrayOf(1.158763101e-15, 1.197917583e-19, 9.338167132e-23, 0.9997735337, 6.659310986e-14, 3.420920097e-25, 5.135685763e-16, 1.04962789e-14, 1.906808201e-17, 8.114332164e-07, 5.317061494e-09, 1.770846178e-15, 1.299319171e-15, 2.961020722e-13, 6.547088058e-07, 8.814642069e-24, 1.628939741e-10, 1.0796775e-16, 7.676494823e-09, 5.770635446e-13, 1.709172059e-18, 2.287245177e-17, 0.0002249869528, 1.539216603e-16),
    )

    @Test
    fun `shipped MLP asset matches numpy on six live vectors`() {
        val asset = File("src/main/assets/classifier/mlp-v1.bin")
        val weights = MlpWeights.parse(source = { asset.inputStream() })
        val classifier = MlpLetterClassifier(weights)
        val csv = File("../../training/data/team-references-v1.csv")
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1)

        assertEquals(24, classifier.supportedLetters.size)
        for (case in rows.indices) {
            val values = csv[rows[case]].split(',')
            val vector = FloatArray(66) { values[it + 1].toFloat() }
            val actual = classifier.probabilities(vector)
            assertEquals("row ${rows[case]} count", expected[case].size, actual.size)
            for (label in actual.indices) {
                assertEquals("row ${rows[case]} label $label", expected[case][label], actual[label], 1e-4)
            }
        }
    }
}
